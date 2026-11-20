package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.*;
import com.java.dev.jim.personal.importer.BillParser;
import com.java.dev.jim.personal.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BillImportService {
    private static final int MAX_ZIP_ENTRIES = 50;
    private static final long MAX_ZIP_UNCOMPRESSED_BYTES = 100L * 1024 * 1024;
    private final Path pendingDirectory;
    private final List<BillParser> parsers;
    private final ImportBatchRepository batchRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final CategoryService categoryService;

    public BillImportService(@Value("${app.bill.import-dir}") String importDir, List<BillParser> parsers,
                             ImportBatchRepository batchRepository, FinanceTransactionRepository transactionRepository, CategoryService categoryService) {
        this.pendingDirectory = Path.of(importDir).toAbsolutePath().normalize(); this.parsers = parsers;
        this.batchRepository = batchRepository; this.transactionRepository = transactionRepository; this.categoryService = categoryService;
    }
    @Transactional
    public List<BatchSummary> scan(Map<String, String> zipPasswords) throws IOException {
        Files.createDirectories(pendingDirectory);
        Map<String, String> passwords = zipPasswords == null ? Map.of() : zipPasswords;
        List<PendingFile> pendingFiles = pendingFiles();
        List<String> missingPasswords = pendingFiles.stream().filter(PendingFile::encrypted)
                .map(PendingFile::fileName).filter(name -> passwords.getOrDefault(name, "").isBlank()).toList();
        if (!missingPasswords.isEmpty()) throw new IllegalArgumentException("请填写加密 ZIP 的密码：" + String.join("、", missingPasswords));
        List<BatchSummary> results = new ArrayList<>();
        try (var files = Files.list(pendingDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList())
                results.addAll(scanPath(file, passwords.get(file.getFileName().toString())));
        }
        return results;
    }
    public List<PendingFile> pendingFiles() throws IOException {
        Files.createDirectories(pendingDirectory);
        List<PendingFile> results = new ArrayList<>();
        try (var files = Files.list(pendingDirectory)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String fileName = file.getFileName().toString();
                if (!isZip(fileName) && !isSupportedBillExtension(fileName)) continue;
                boolean encrypted = false;
                if (isZip(fileName)) {
                    try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(file.toFile())) {
                        encrypted = zip.isEncrypted();
                    } catch (ZipException exception) {
                        throw new IllegalArgumentException("无法读取 ZIP 文件：" + fileName, exception);
                    }
                }
                results.add(new PendingFile(fileName, isZip(fileName), encrypted));
            }
        }
        return results;
    }
    @Transactional
    public List<UploadSummary> upload(List<MultipartFile> files, String zipPassword) throws IOException {
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("请选择要上传的账单文件");
        Files.createDirectories(pendingDirectory);
        Path processingDirectory = pendingDirectory.getParent().resolve("processing");
        Files.createDirectories(processingDirectory);
        List<UploadSummary> results = new ArrayList<>();
        for (MultipartFile file : files) results.addAll(uploadFile(file, processingDirectory, zipPassword));
        return results;
    }
    private List<UploadSummary> uploadFile(MultipartFile file, Path processingDirectory, String zipPassword) {
        String originalFileName = file.getOriginalFilename();
        String fileName = originalFileName == null || originalFileName.isBlank() ? "未命名文件" : originalFileName;
        Path temporaryFile = null;
        try {
            fileName = safeFileName(originalFileName);
            if (file.isEmpty()) return List.of(UploadSummary.failed(fileName, "文件内容为空"));
            temporaryFile = Files.createTempFile(processingDirectory, "upload-", "-" + fileName);
            file.transferTo(temporaryFile);
            String hash = sha256(temporaryFile);
            Optional<ImportBatch> known = batchRepository.findByFileHash(hash);
            if (known.isPresent() && known.get().getStatus() != ImportBatchStatus.FAILED) {
                Files.deleteIfExists(temporaryFile);
                return List.of(UploadSummary.duplicate(fileName, BatchSummary.from(known.get(), "文件内容已扫描")));
            }
            if (isZip(fileName)) {
                Path pendingFile = uniquePendingFile(fileName);
                Files.move(temporaryFile, pendingFile);
                temporaryFile = null;
                List<BatchSummary> batches = scanZip(pendingFile, zipPassword);
                return batches.stream().map(batch -> batch.status().equals(ImportBatchStatus.FAILED.name())
                        ? UploadSummary.failed(batch.fileName(), batch.errorMessage(), batch)
                        : UploadSummary.uploaded(batch.fileName(), batch)).toList();
            }
            BillParser parser = parserFor(temporaryFile, fileName);
            Path pendingFile = uniquePendingFile(fileName);
            Files.move(temporaryFile, pendingFile);
            temporaryFile = null;
            BatchSummary batch = scanFile(pendingFile, parser, hash);
            return List.of(batch.status().equals(ImportBatchStatus.FAILED.name())
                    ? UploadSummary.failed(fileName, batch.errorMessage(), batch)
                    : UploadSummary.uploaded(fileName, batch));
        } catch (Exception exception) {
            return List.of(UploadSummary.failed(fileName, exception.getMessage()));
        } finally {
            if (temporaryFile != null) {
                try { Files.deleteIfExists(temporaryFile); } catch (IOException ignored) { }
            }
        }
    }
    private List<BatchSummary> scanPath(Path file, String zipPassword) throws IOException {
        return isZip(file.getFileName().toString()) ? scanZip(file, zipPassword) : List.of(scanFile(file));
    }
    private List<BatchSummary> scanZip(Path zipPath, String zipPassword) throws IOException {
        Path processingDirectory = pendingDirectory.getParent().resolve("processing");
        Files.createDirectories(processingDirectory);
        List<ExtractedBill> extractedFiles = extractZip(zipPath, processingDirectory, zipPassword);
        if (extractedFiles.isEmpty()) throw new IllegalArgumentException("ZIP 中没有支持的 Excel、CSV 或 PDF 账单");
        List<BatchSummary> results = new ArrayList<>();
        for (ExtractedBill extractedFile : extractedFiles) {
            Path pendingFile = uniquePendingFile(extractedFile.fileName());
            Files.move(extractedFile.path(), pendingFile);
            results.add(scanFile(pendingFile));
        }
        archiveSourcePackage(zipPath);
        return results;
    }
    private List<ExtractedBill> extractZip(Path zipPath, Path processingDirectory, String zipPassword) throws IOException {
        List<ExtractedBill> extracted = new ArrayList<>();
        long totalBytes = 0;
        char[] password = zipPassword == null ? new char[0] : zipPassword.toCharArray();
        try (net.lingala.zip4j.ZipFile zip = new net.lingala.zip4j.ZipFile(zipPath.toFile(), password)) {
            if (zip.isEncrypted() && password.length == 0) throw new IllegalArgumentException("ZIP 已加密，请输入压缩包密码");
            List<FileHeader> entries = zip.getFileHeaders();
            if (entries.size() > MAX_ZIP_ENTRIES) throw new IllegalArgumentException("ZIP 文件条目超过 " + MAX_ZIP_ENTRIES + " 个");
            for (FileHeader entry : entries) {
                if (entry.isDirectory() || entry.getFileName().startsWith("__MACOSX/") || entry.getFileName().endsWith(".DS_Store")) continue;
                String fileName = safeFileName(entry.getFileName());
                if (isZip(fileName)) throw new IllegalArgumentException("不支持嵌套 ZIP：" + fileName);
                if (!isSupportedBillExtension(fileName)) continue;
                Path target = Files.createTempFile(processingDirectory, "zip-", "-" + fileName);
                try (var input = zip.getInputStream(entry); var output = Files.newOutputStream(target)) {
                    byte[] buffer = new byte[8192]; int length;
                    while ((length = input.read(buffer)) != -1) {
                        totalBytes += length;
                        if (totalBytes > MAX_ZIP_UNCOMPRESSED_BYTES) throw new IllegalArgumentException("ZIP 解压后总大小超过 100MB");
                        output.write(buffer, 0, length);
                    }
                } catch (IOException | RuntimeException exception) {
                    Files.deleteIfExists(target);
                    throw exception;
                }
                extracted.add(new ExtractedBill(target, fileName));
            }
        } catch (ZipException exception) {
            for (ExtractedBill file : extracted) Files.deleteIfExists(file.path());
            throw new IllegalArgumentException("ZIP 解压失败，请检查压缩包密码是否正确", exception);
        } catch (IOException | RuntimeException exception) {
            for (ExtractedBill file : extracted) Files.deleteIfExists(file.path());
            throw exception;
        } finally {
            Arrays.fill(password, '\0');
        }
        return extracted;
    }
    private void archiveSourcePackage(Path zipPath) throws IOException {
        Path directory = pendingDirectory.getParent().resolve("archive/source-packages");
        Files.createDirectories(directory);
        Path target = directory.resolve(zipPath.getFileName());
        if (Files.exists(target)) target = directory.resolve(System.currentTimeMillis() + "-" + zipPath.getFileName());
        Files.move(zipPath, target);
    }
    private boolean isZip(String fileName) { return fileName.toLowerCase(Locale.ROOT).endsWith(".zip"); }
    private boolean isSupportedBillExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".csv") || lower.endsWith(".pdf");
    }
    private record ExtractedBill(Path path, String fileName) { }
    private BatchSummary scanFile(Path file) throws IOException {
        String hash = sha256(file);
        return scanFile(file, parserFor(file), hash);
    }
    private BatchSummary scanFile(Path file, BillParser parser, String hash) throws IOException {
        String name = file.getFileName().toString();
        Optional<ImportBatch> known = batchRepository.findByFileHash(hash);
        if (known.isPresent() && known.get().getStatus() != ImportBatchStatus.FAILED) return BatchSummary.from(known.get(), "文件内容已扫描");
        ImportBatch batch;
        if (known.isPresent()) {
            batch = known.get();
            if (transactionRepository.existsByImportBatchId(batch.getId()))
                throw new IllegalStateException("失败批次已包含交易记录，无法自动重试：" + name);
            batch.retry();
        } else batch = new ImportBatch(parser.source(), name, hash);
        try {
            List<ParsedTransaction> parsed = parser.parse(file); int duplicates = 0;
            batch = batchRepository.save(batch);
            for (ParsedTransaction item : parsed) {
                boolean duplicate = (!item.externalId().isBlank() && transactionRepository.existsBySourceAndExternalId(item.source(), item.externalId()))
                        || transactionRepository.existsByBusinessFingerprint(item.businessFingerprint());
                if (duplicate) { duplicates++; continue; }
                FinanceTransaction transaction = new FinanceTransaction(batch, item);
                categoryService.applyAutomaticCategory(transaction);
                transactionRepository.save(transaction);
            }
            batch.setCounts(parsed.size(), duplicates); batchRepository.save(batch);
        } catch (Exception exception) {
            batch.fail(exception.getMessage()); batchRepository.save(batch);
        }
        return BatchSummary.from(batch, batch.getErrorMessage() == null ? "等待确认" : "解析失败");
    }
    private BillParser parserFor(Path file) {
        return parserFor(file, file.getFileName().toString());
    }
    private BillParser parserFor(Path file, String displayName) {
        return parsers.stream().filter(candidate -> candidate.supports(file)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的账单文件：" + displayName));
    }
    private String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) throw new IllegalArgumentException("账单文件名不能为空");
        String normalized = originalFileName.replace('\\', '/');
        String fileName = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "_").trim();
        if (fileName.isBlank() || fileName.length() > 200) throw new IllegalArgumentException("账单文件名无效");
        return fileName;
    }
    private Path uniquePendingFile(String fileName) {
        Path target = pendingDirectory.resolve(fileName);
        if (!Files.exists(target)) return target;
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName;
        String extension = extensionIndex > 0 ? fileName.substring(extensionIndex) : "";
        int suffix = 2;
        while (Files.exists(target)) target = pendingDirectory.resolve(baseName + " (" + suffix++ + ")" + extension);
        return target;
    }
    @Transactional
    public BatchSummary confirm(Long batchId) {
        ImportBatch batch = batchRepository.findById(batchId).orElseThrow(() -> new NoSuchElementException("导入批次不存在"));
        if (batch.getStatus() != ImportBatchStatus.PENDING_CONFIRMATION) throw new IllegalStateException("当前批次不可确认");
        batch.confirm();
        ImportBatch confirmed = batchRepository.save(batch);
        archive(confirmed);
        return BatchSummary.from(confirmed, "已入账");
    }
    public List<BatchSummary> batches() { return batchRepository.findAll().stream().sorted(Comparator.comparing(ImportBatch::getCreatedAt).reversed()).map(batch -> BatchSummary.from(batch, "")).toList(); }
    private String sha256(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[8192]; int length;
            while ((length = input.read(buffer)) != -1) digest.update(buffer, 0, length);
            StringBuilder result = new StringBuilder(); for (byte item : digest.digest()) result.append(String.format("%02x", item)); return result.toString();
        } catch (Exception exception) { throw new IOException("无法计算文件哈希", exception); }
    }
    private void archive(ImportBatch batch) {
        Path original = pendingDirectory.resolve(batch.getFileName());
        if (!Files.isRegularFile(original)) return;
        try {
            Path archiveDirectory = pendingDirectory.getParent().resolve("archived");
            Files.createDirectories(archiveDirectory);
            Path target = archiveDirectory.resolve(batch.getFileName());
            if (Files.exists(target)) target = archiveDirectory.resolve(batch.getId() + "-" + batch.getFileName());
            Files.move(original, target);
        } catch (IOException exception) {
            throw new IllegalStateException("账单已入账，但归档原文件失败：" + exception.getMessage(), exception);
        }
    }
    public record BatchSummary(Long id, String source, String fileName, String status, int totalCount, int duplicateCount,
                               String errorMessage, LocalDateTime createdAt, String message) {
        static BatchSummary from(ImportBatch batch, String message) {
            return new BatchSummary(batch.getId(), batch.getSource().name(), batch.getFileName(), batch.getStatus().name(),
                    batch.getTotalCount(), batch.getDuplicateCount(), batch.getErrorMessage(), batch.getCreatedAt(), message);
        }
    }
    public record UploadSummary(String fileName, String status, String message, BatchSummary batch) {
        static UploadSummary uploaded(String fileName, BatchSummary batch) {
            return new UploadSummary(fileName, "UPLOADED", "上传成功，等待确认入账", batch);
        }
        static UploadSummary duplicate(String fileName, BatchSummary batch) {
            return new UploadSummary(fileName, "DUPLICATE", "文件内容已上传，不重复入库", batch);
        }
        static UploadSummary failed(String fileName, String message) { return failed(fileName, message, null); }
        static UploadSummary failed(String fileName, String message, BatchSummary batch) {
            return new UploadSummary(fileName, "FAILED", message == null || message.isBlank() ? "上传失败" : message, batch);
        }
    }
    public record PendingFile(String fileName, boolean zip, boolean encrypted) { }
}
