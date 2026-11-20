package com.java.dev.jim.personal.importer;

import com.java.dev.jim.personal.domain.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

final class ParserSupport {
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"), DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"));
    private ParserSupport() { }
    static String cell(Map<String, String> row, String... keys) {
        for (String key : keys) {
            String value = row.get(normalizeHeader(key));
            if (value != null && !value.isBlank()) return value.trim();
        }
        return "";
    }
    static String normalizeHeader(String value) {
        return value == null ? "" : value.replace("\uFEFF", "").replaceAll("[\\s（）()_-]", "").trim();
    }
    static LocalDateTime date(String value) {
        String normalized = value.trim().replace("/", "-");
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try { return LocalDateTime.parse(normalized, formatter); } catch (DateTimeParseException ignored) { }
        }
        try { return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay(); }
        catch (DateTimeParseException ignored) { }
        throw new IllegalArgumentException("无法识别交易时间：" + value);
    }
    static BigDecimal amount(String value) {
        String normalized = value.replaceAll("[￥¥,\\s]", "").replace("+", "");
        if (normalized.isBlank()) throw new IllegalArgumentException("缺少交易金额");
        return new BigDecimal(normalized).abs();
    }
    static TransactionDirection direction(String value) {
        if (value.contains("支出") || value.contains("付款") || value.contains("扣款") || value.contains("借")) return TransactionDirection.EXPENSE;
        if (value.contains("收入") || value.contains("收款") || value.contains("入账") || value.contains("贷")) return TransactionDirection.INCOME;
        return TransactionDirection.NEUTRAL;
    }
    static String fingerprint(BillSource source, String account, LocalDateTime occurredAt, TransactionDirection direction,
                              BigDecimal amount, String counterparty, String summary) {
        return sha256(String.join("|", source.name(), normalize(account), occurredAt.toString(), direction.name(),
                amount.stripTrailingZeros().toPlainString(), normalize(counterparty), normalize(summary)));
    }
    static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(64);
            for (byte value : digest) result.append(String.format("%02x", value));
            return result.toString();
        } catch (Exception exception) { throw new IllegalStateException("无法生成 SHA-256", exception); }
    }
    static String json(Map<String, String> row) {
        return "{}";
    }
    static String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", "").trim().toLowerCase(Locale.ROOT); }
}
