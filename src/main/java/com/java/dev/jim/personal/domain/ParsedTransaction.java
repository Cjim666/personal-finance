package com.java.dev.jim.personal.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ParsedTransaction(
        BillSource source, String externalId, String businessFingerprint, LocalDateTime occurredAt,
        TransactionDirection direction, BigDecimal amount, String accountName, String counterparty,
        String summary, String category, String rawData) { }
