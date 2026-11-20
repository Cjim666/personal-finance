package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.FinanceTransaction;
import com.java.dev.jim.personal.domain.ImportBatchStatus;
import com.java.dev.jim.personal.domain.TransactionDirection;
import com.java.dev.jim.personal.repository.FinanceTransactionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTests {
    private final FinanceTransactionRepository repository = mock(FinanceTransactionRepository.class);
    private final ReportService service = new ReportService(repository);

    @Test
    void weekReportBuildsWeeklyExpensesForSelectedMonth() {
        FinanceTransaction firstWeek = expense("2026-08-03T10:00:00", "100");
        FinanceTransaction thirdWeek = expense("2026-08-18T10:00:00", "250");
        List<FinanceTransaction> currentWeek = List.of(thirdWeek);
        List<FinanceTransaction> wholeMonth = List.of(firstWeek, thirdWeek);
        stub(LocalDateTime.of(2026, 8, 10, 0, 0), LocalDateTime.of(2026, 8, 17, 0, 0), currentWeek);
        stub(LocalDateTime.of(2026, 8, 1, 0, 0), LocalDateTime.of(2026, 9, 1, 0, 0), wholeMonth);

        var result = service.report("WEEK", LocalDate.of(2026, 8, 12), null, null);

        assertEquals(List.of("8/1～8/2", "8/3～8/9", "8/10～8/16", "8/17～8/23", "8/24～8/30", "8/31～8/31"),
                result.expenseByPeriod().stream().map(ReportService.PeriodExpense::label).toList());
        assertEquals(new BigDecimal("100"), result.expenseByPeriod().get(1).amount());
        assertEquals(new BigDecimal("250"), result.expenseByPeriod().get(3).amount());
    }

    @Test
    void monthReportBuildsAllMonthsForSelectedYear() {
        FinanceTransaction january = expense("2026-01-05T10:00:00", "80");
        FinanceTransaction december = expense("2026-12-05T10:00:00", "120");
        stub(LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0), List.of());
        stub(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0), List.of(january, december));

        var result = service.report("MONTH", LocalDate.of(2026, 6, 15), null, null);

        assertEquals(12, result.expenseByPeriod().size());
        assertEquals(new BigDecimal("80"), result.expenseByPeriod().getFirst().amount());
        assertEquals(new BigDecimal("120"), result.expenseByPeriod().getLast().amount());
    }

    @Test
    void yearReportBuildsRecentFiveYears() {
        FinanceTransaction oldest = expense("2022-03-05T10:00:00", "60");
        FinanceTransaction newest = expense("2026-03-05T10:00:00", "160");
        stub(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0), List.of(newest));
        stub(LocalDateTime.of(2022, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0), List.of(oldest, newest));

        var result = service.report("YEAR", LocalDate.of(2026, 6, 15), null, null);

        assertEquals(List.of("2022年", "2023年", "2024年", "2025年", "2026年"),
                result.expenseByPeriod().stream().map(ReportService.PeriodExpense::label).toList());
        assertEquals(new BigDecimal("60"), result.expenseByPeriod().getFirst().amount());
        assertEquals(new BigDecimal("160"), result.expenseByPeriod().getLast().amount());
    }

    @Test
    void ignoredCategoryRemainsVisibleButIsExcludedFromAllStatistics() {
        FinanceTransaction normal = expense("2026-06-05T10:00:00", "80");
        FinanceTransaction ignored = expense("2026-06-06T10:00:00", "120");
        when(ignored.getCategory()).thenReturn("忽略");
        List<FinanceTransaction> transactions = List.of(normal, ignored);
        stub(LocalDateTime.of(2026, 6, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0), transactions);
        stub(LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2027, 1, 1, 0, 0), transactions);

        var result = service.report("MONTH", LocalDate.of(2026, 6, 15), null, null);

        assertEquals(new BigDecimal("80"), result.expense());
        assertEquals(new BigDecimal("80"), result.expenseByCategory().get("餐饮"));
        assertEquals(2, result.transactions().size());
        assertEquals(new BigDecimal("80"), result.expenseByPeriod().get(5).amount());
    }

    private FinanceTransaction expense(String occurredAt, String amount) {
        FinanceTransaction transaction = mock(FinanceTransaction.class);
        when(transaction.getDirection()).thenReturn(TransactionDirection.EXPENSE);
        when(transaction.getAmount()).thenReturn(new BigDecimal(amount));
        when(transaction.getOccurredAt()).thenReturn(LocalDateTime.parse(occurredAt));
        when(transaction.getCategory()).thenReturn("餐饮");
        return transaction;
    }

    private void stub(LocalDateTime from, LocalDateTime to, List<FinanceTransaction> transactions) {
        List<FinanceTransactionRepository.DailyExpense> daily = new ArrayList<>();
        for (var transaction : transactions) {
            if (transaction.getDirection() != TransactionDirection.EXPENSE || "忽略".equals(transaction.getCategory())) continue;
            var occurredAt = transaction.getOccurredAt();
            var amount = transaction.getAmount();
            var row = mock(FinanceTransactionRepository.DailyExpense.class);
            when(row.getYear()).thenReturn(occurredAt.getYear());
            when(row.getMonth()).thenReturn(occurredAt.getMonthValue());
            when(row.getDay()).thenReturn(occurredAt.getDayOfMonth());
            when(row.getAmount()).thenReturn(amount);
            daily.add(row);
        }
        when(repository.dailyExpenses(eq(ImportBatchStatus.CONFIRMED), eq(from), eq(to))).thenReturn(daily);
        when(repository.findByImportBatchStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                eq(ImportBatchStatus.CONFIRMED), eq(from), eq(to))).thenReturn(transactions);
    }
}
