package com.java.dev.jim.personal.domain;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "finance_transaction")
public class FinanceTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "import_batch_id", nullable = false)
    private ImportBatch importBatch;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private BillSource source;
    private String externalId;
    @Column(nullable = false, length = 64, columnDefinition = "CHAR(64)", unique = true) private String businessFingerprint;
    @Column(nullable = false) private LocalDateTime occurredAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private TransactionDirection direction;
    @Column(nullable = false, precision = 19, scale = 4) private BigDecimal amount;
    private String accountName;
    private String counterparty;
    @Column(length = 1000) private String summary;
    private String category;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "category_id")
    private Category categoryDefinition;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private CategoryOrigin categoryOrigin;
    @Column(columnDefinition = "json") private String rawData;
    @Column(nullable = false) private LocalDateTime createdAt;

    protected FinanceTransaction() { }
    public FinanceTransaction(ImportBatch batch, ParsedTransaction parsed) {
        this.importBatch = batch; this.source = parsed.source(); this.externalId = parsed.externalId().isBlank() ? null : parsed.externalId();
        this.businessFingerprint = parsed.businessFingerprint(); this.occurredAt = parsed.occurredAt();
        this.direction = parsed.direction(); this.amount = parsed.amount(); this.accountName = parsed.accountName();
        this.counterparty = parsed.counterparty(); this.summary = parsed.summary(); this.category = parsed.category();
        this.rawData = parsed.rawData(); this.categoryOrigin = CategoryOrigin.UNCLASSIFIED; this.createdAt = LocalDateTime.now();
    }
    public Long getId() { return id; }
    public BillSource getSource() { return source; }
    public String getExternalId() { return externalId; }
    public LocalDateTime getOccurredAt() { return occurredAt; }
    public TransactionDirection getDirection() { return direction; }
    public BigDecimal getAmount() { return amount; }
    // Preserve all DECIMAL(19,4) digits when sorting in the browser.
    public String getAmountUnits() { return amount.movePointRight(4).toBigIntegerExact().toString(); }
    public String getAccountName() { return accountName; }
    public String getCounterparty() { return counterparty; }
    public String getSummary() { return summary; }
    public String getCategory() { return category; }
    @JsonIgnore public Category getCategoryDefinition() { return categoryDefinition; }
    public CategoryOrigin getCategoryOrigin() { return categoryOrigin; }
    public void setCategory(Category category, CategoryOrigin origin) {
        this.categoryDefinition = category; this.category = category == null ? "未分类" : category.getName(); this.categoryOrigin = origin;
    }
}
