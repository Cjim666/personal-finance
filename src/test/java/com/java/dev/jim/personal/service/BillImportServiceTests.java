package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.BillSource;
import com.java.dev.jim.personal.domain.ImportBatch;
import com.java.dev.jim.personal.domain.ImportBatchStatus;
import com.java.dev.jim.personal.domain.ParsedTransaction;
import com.java.dev.jim.personal.importer.BillParser;
import com.java.dev.jim.personal.repository.FinanceTransactionRepository;
import com.java.dev.jim.personal.repository.ImportBatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.EncryptionMethod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillImportServiceTests {
    @TempDir Path temporaryDirectory;

    @Test void confirmArchivesOriginalFile() throws Exception {
        Path pendingDirectory = temporaryDirectory.resolve("pending");
        Files.createDirectories(pendingDirectory);
        Path original = pendingDirectory.resolve("statement.csv");
        Files.writeString(original, "test");

        ImportBatch batch = new ImportBatch(BillSource.ALIPAY, "statement.csv", "hash");
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        when(batches.findById(1L)).thenReturn(Optional.of(batch));
        when(batches.save(batch)).thenReturn(batch);
        BillImportService service = new BillImportService(pendingDirectory.toString(), List.of(), batches, mock(FinanceTransactionRepository.class), mock(CategoryService.class));

        service.confirm(1L);

        assertEquals(ImportBatchStatus.CONFIRMED, batch.getStatus());
        assertFalse(Files.exists(original));
        assertTrue(Files.exists(temporaryDirectory.resolve("archived/statement.csv")));
    }

    @Test void uploadProcessesMultipleFilesAndCreatesPendingBatches() throws Exception {
        Path pendingDirectory = temporaryDirectory.resolve("pending");
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        when(batches.findByFileHash(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(batches.save(org.mockito.ArgumentMatchers.any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BillParser parser = mock(BillParser.class);
        when(parser.supports(org.mockito.ArgumentMatchers.any(Path.class))).thenReturn(true);
        when(parser.source()).thenReturn(BillSource.ALIPAY);
        when(parser.parse(org.mockito.ArgumentMatchers.any(Path.class))).thenReturn(List.<ParsedTransaction>of());
        BillImportService service = new BillImportService(pendingDirectory.toString(), List.of(parser), batches,
                mock(FinanceTransactionRepository.class), mock(CategoryService.class));

        var results = service.upload(List.of(
                new MockMultipartFile("files", "支付宝账单-1.csv", "text/csv", "first".getBytes()),
                new MockMultipartFile("files", "支付宝账单-2.csv", "text/csv", "second".getBytes())), null);

        assertEquals(2, results.size());
        assertTrue(results.stream().allMatch(result -> result.status().equals("UPLOADED")));
        assertTrue(Files.exists(pendingDirectory.resolve("支付宝账单-1.csv")));
        assertTrue(Files.exists(pendingDirectory.resolve("支付宝账单-2.csv")));
        verify(parser, org.mockito.Mockito.times(2)).parse(org.mockito.ArgumentMatchers.any(Path.class));
    }

    @Test void uploadDoesNotKeepFileWhenContentWasAlreadyImported() throws Exception {
        Path pendingDirectory = temporaryDirectory.resolve("pending");
        byte[] content = "duplicate".getBytes();
        ImportBatch known = new ImportBatch(BillSource.ALIPAY, "支付宝账单.csv", "known-hash");
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        when(batches.findByFileHash(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of(known));
        BillParser parser = mock(BillParser.class);
        when(parser.supports(org.mockito.ArgumentMatchers.any(Path.class))).thenReturn(true);
        BillImportService service = new BillImportService(pendingDirectory.toString(), List.of(parser), batches,
                mock(FinanceTransactionRepository.class), mock(CategoryService.class));

        var result = service.upload(List.of(new MockMultipartFile("files", "支付宝账单.csv", "text/csv", content)), null).getFirst();

        assertEquals("DUPLICATE", result.status());
        assertFalse(Files.exists(pendingDirectory.resolve("支付宝账单.csv")));
        try (var files = Files.list(temporaryDirectory.resolve("processing"))) { assertTrue(files.findAny().isEmpty()); }
    }

    @Test void uploadRejectsUnsupportedFileWithoutStoppingOtherFiles() throws Exception {
        Path pendingDirectory = temporaryDirectory.resolve("pending");
        BillImportService service = new BillImportService(pendingDirectory.toString(), List.of(), mock(ImportBatchRepository.class),
                mock(FinanceTransactionRepository.class), mock(CategoryService.class));

        var results = service.upload(List.of(
                new MockMultipartFile("files", "notes.txt", "text/plain", "unsupported".getBytes()),
                new MockMultipartFile("files", "empty.csv", "text/csv", new byte[0])), null);

        assertEquals(List.of("FAILED", "FAILED"), results.stream().map(BillImportService.UploadSummary::status).toList());
        assertEquals("不支持的账单文件：notes.txt", results.getFirst().message());
        try (var files = Files.list(temporaryDirectory.resolve("processing"))) { assertTrue(files.findAny().isEmpty()); }
    }

    @Test void scanExtractsEncryptedZipBillsAndArchivesSourcePackage() throws Exception {
        Path pendingDirectory = temporaryDirectory.resolve("pending");
        Files.createDirectories(pendingDirectory);
        Path zipPath = pendingDirectory.resolve("支付宝账单.zip");
        Path bill = temporaryDirectory.resolve("支付宝交易明细.csv");
        Files.writeString(bill, "bill-content");
        ZipParameters parameters = new ZipParameters();
        parameters.setEncryptFiles(true);
        parameters.setEncryptionMethod(EncryptionMethod.AES);
        new ZipFile(zipPath.toFile(), "secret".toCharArray()).addFile(bill.toFile(), parameters);
        ImportBatchRepository batches = mock(ImportBatchRepository.class);
        when(batches.findByFileHash(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(batches.save(org.mockito.ArgumentMatchers.any(ImportBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        BillParser parser = mock(BillParser.class);
        when(parser.supports(org.mockito.ArgumentMatchers.any(Path.class))).thenAnswer(invocation -> invocation.<Path>getArgument(0).toString().endsWith(".csv"));
        when(parser.source()).thenReturn(BillSource.ALIPAY);
        when(parser.parse(org.mockito.ArgumentMatchers.any(Path.class))).thenReturn(List.of());
        BillImportService service = new BillImportService(pendingDirectory.toString(), List.of(parser), batches,
                mock(FinanceTransactionRepository.class), mock(CategoryService.class));

        var pendingFiles = service.pendingFiles();
        var results = service.scan(Map.of("支付宝账单.zip", "secret"));

        assertEquals(List.of(new BillImportService.PendingFile("支付宝账单.zip", true, true)), pendingFiles);
        assertEquals(1, results.size());
        assertEquals("支付宝交易明细.csv", results.getFirst().fileName());
        assertTrue(Files.exists(pendingDirectory.resolve("支付宝交易明细.csv")));
        assertFalse(Files.exists(zipPath));
        assertTrue(Files.exists(temporaryDirectory.resolve("archive/source-packages/支付宝账单.zip")));
    }
}
