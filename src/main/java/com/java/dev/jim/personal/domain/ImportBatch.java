package com.java.dev.jim.personal.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_batch")
public class ImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private BillSource source;
    @Column(nullable = false) private String fileName;
    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)", unique = true) private String fileHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private ImportBatchStatus status;
    @Column(nullable = false) private int totalCount;
    @Column(nullable = false) private int duplicateCount;
    private String errorMessage;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;

    protected ImportBatch() { }
    public ImportBatch(BillSource source, String fileName, String fileHash) {
        this.source = source; this.fileName = fileName; this.fileHash = fileHash;
        this.status = ImportBatchStatus.PENDING_CONFIRMATION; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public BillSource getSource() { return source; }
    public String getFileName() { return fileName; }
    public String getFileHash() { return fileHash; }
    public ImportBatchStatus getStatus() { return status; }
    public int getTotalCount() { return totalCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCounts(int totalCount, int duplicateCount) { this.totalCount = totalCount; this.duplicateCount = duplicateCount; }
    public void fail(String message) { this.status = ImportBatchStatus.FAILED; this.errorMessage = message; }
    public void retry() { this.status = ImportBatchStatus.PENDING_CONFIRMATION; this.totalCount = 0; this.duplicateCount = 0; this.errorMessage = null; }
    public void confirm() { this.status = ImportBatchStatus.CONFIRMED; this.confirmedAt = LocalDateTime.now(); }
}
