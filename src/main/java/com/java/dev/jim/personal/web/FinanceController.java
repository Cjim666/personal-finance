package com.java.dev.jim.personal.web;

import com.java.dev.jim.personal.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api")
public class FinanceController {
    private final BillImportService importService; private final ReportService reportService;
    public FinanceController(BillImportService importService, ReportService reportService) { this.importService = importService; this.reportService = reportService; }
    @GetMapping("/imports/pending-files") public List<BillImportService.PendingFile> pendingFiles() throws Exception { return importService.pendingFiles(); }
    @PostMapping("/imports/scan") public List<BillImportService.BatchSummary> scan(@RequestBody(required = false) ScanRequest request) throws Exception {
        return importService.scan(request == null ? Map.of() : request.zipPasswords());
    }
    @PostMapping(value = "/imports/upload", consumes = "multipart/form-data")
    public List<BillImportService.UploadSummary> upload(@RequestPart("files") List<MultipartFile> files,
            @RequestParam(required = false) String zipPassword) throws Exception { return importService.upload(files, zipPassword); }
    @GetMapping("/imports") public List<BillImportService.BatchSummary> imports() { return importService.batches(); }
    @PostMapping("/imports/{id}/confirm") public BillImportService.BatchSummary confirm(@PathVariable Long id) { return importService.confirm(id); }
    @GetMapping("/reports") public ReportService.Report report(@RequestParam(defaultValue = "MONTH") String period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) { return reportService.report(period, date, from, to); }
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, NoSuchElementException.class})
    ResponseEntity<Map<String, String>> invalid(RuntimeException exception) { return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage())); }
    public record ScanRequest(Map<String, String> zipPasswords) { }
}
