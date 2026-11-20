package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** Finds review candidates without changing transactions or deciding which entry counts. */
@Service
public class DuplicateReviewService {
    public List<Candidate> review(List<FinanceTransaction> transactions) {
        Map<ContentKey, List<FinanceTransaction>> sameContent = new LinkedHashMap<>();
        Map<PaymentKey, List<FinanceTransaction>> crossSource = new LinkedHashMap<>();
        for (var t : transactions) {
            BigDecimal amount = t.getAmount().stripTrailingZeros();
            sameContent.computeIfAbsent(new ContentKey(t.getSource(), t.getOccurredAt(), t.getDirection(), amount,
                    t.getAccountName(), t.getCounterparty(), t.getSummary()), key -> new ArrayList<>()).add(t);
            if (t.getDirection() != TransactionDirection.NEUTRAL && !"忽略".equals(t.getCategory()))
                crossSource.computeIfAbsent(new PaymentKey(t.getOccurredAt().toLocalDate(), t.getDirection(), amount),
                        key -> new ArrayList<>()).add(t);
        }
        List<Candidate> result = new ArrayList<>();
        sameContent.values().stream().filter(rows -> rows.size() > 1).forEach(rows -> result.add(candidate("SAME_CONTENT", rows)));
        crossSource.values().stream().filter(rows -> rows.stream().map(FinanceTransaction::getSource).distinct().count() > 1)
                .forEach(rows -> result.add(candidate("CROSS_SOURCE_SAME_DAY_AMOUNT", rows)));
        return result;
    }

    private Candidate candidate(String reason, List<FinanceTransaction> rows) {
        return new Candidate(reason, "仅为疑似重复，请核对原账单与支付方式；未调整统计。",
                rows.stream().map(t -> new Entry(t.getId(), t.getSource(), t.getOccurredAt().toLocalDate(),
                        t.getDirection(), t.getAmount(), t.getCategory())).toList());
    }
    private record ContentKey(BillSource source, LocalDateTime time, TransactionDirection direction,
                              BigDecimal amount, String account, String counterparty, String summary) { }
    private record PaymentKey(LocalDate date, TransactionDirection direction, BigDecimal amount) { }
    public record Entry(Long id, BillSource source, LocalDate date, TransactionDirection direction,
                        BigDecimal amount, String category) { }
    public record Candidate(String reason, String message, List<Entry> entries) { }
}
