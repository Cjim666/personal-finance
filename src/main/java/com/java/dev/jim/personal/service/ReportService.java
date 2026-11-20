package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.*;
import com.java.dev.jim.personal.repository.FinanceTransactionRepository;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.*;
import java.util.*;

@Service
public class ReportService {
    private static final String IGNORED_CATEGORY = "忽略";
    private final FinanceTransactionRepository repository;
    public ReportService(FinanceTransactionRepository repository) { this.repository = repository; }
    public Report report(String period, LocalDate date, LocalDate from, LocalDate to) {
        LocalDate base = date == null ? LocalDate.now() : date;
        String normalizedPeriod = period == null ? "MONTH" : period.toUpperCase();
        DateRange range = range(normalizedPeriod, base, from, to);
        List<FinanceTransaction> transactions = repository.findByImportBatchStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
                ImportBatchStatus.CONFIRMED, range.from().atStartOfDay(), range.to().atStartOfDay());
        BigDecimal income = sum(transactions, TransactionDirection.INCOME);
        BigDecimal expense = sum(transactions, TransactionDirection.EXPENSE);
        Map<String, BigDecimal> expenseByCategory = new TreeMap<>();
        for (FinanceTransaction transaction : transactions) if (includedInStatistics(transaction) && transaction.getDirection() == TransactionDirection.EXPENSE)
            expenseByCategory.merge(blankAs(transaction.getCategory(), "未分类"), transaction.getAmount(), BigDecimal::add);
        DateRange trendRange = trendRange(normalizedPeriod, base, range);
        List<DailyExpense> trend = normalizedPeriod.equals("CUSTOM") ? List.of() : repository.dailyExpenses(
                ImportBatchStatus.CONFIRMED, trendRange.from().atStartOfDay(), trendRange.to().atStartOfDay()).stream()
                .map(row -> new DailyExpense(LocalDate.of(row.getYear(), row.getMonth(), row.getDay()), row.getAmount())).toList();
        List<PeriodExpense> expenseByPeriod = expenseByPeriod(normalizedPeriod, base, trend);
        return new Report(normalizedPeriod, range.from(), range.to().minusDays(1), income, expense, income.subtract(expense), expenseByCategory, expenseByPeriod, transactions);
    }

    private DateRange trendRange(String period, LocalDate base, DateRange currentRange) {
        return switch (period) {
            case "WEEK" -> new DateRange(base.withDayOfMonth(1), base.withDayOfMonth(1).plusMonths(1));
            case "MONTH" -> new DateRange(base.withDayOfYear(1), base.withDayOfYear(1).plusYears(1));
            case "YEAR" -> new DateRange(LocalDate.of(base.getYear() - 4, 1, 1), LocalDate.of(base.getYear() + 1, 1, 1));
            default -> currentRange;
        };
    }

    private List<PeriodExpense> expenseByPeriod(String value, LocalDate base, List<DailyExpense> transactions) {
        return switch (value == null ? "MONTH" : value.toUpperCase()) {
            case "WEEK" -> weeklyExpenses(base, transactions);
            case "YEAR" -> yearlyExpenses(base, transactions);
            case "CUSTOM" -> List.of();
            default -> monthlyExpenses(base, transactions);
        };
    }

    private List<PeriodExpense> weeklyExpenses(LocalDate base, List<DailyExpense> transactions) {
        LocalDate monthStart = base.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        LocalDate weekStart = monthStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        List<PeriodExpense> result = new ArrayList<>();
        while (weekStart.isBefore(monthEnd)) {
            LocalDate visibleStart = weekStart.isBefore(monthStart) ? monthStart : weekStart;
            LocalDate visibleEnd = weekStart.plusDays(7).isAfter(monthEnd) ? monthEnd.minusDays(1) : weekStart.plusDays(6);
            BigDecimal amount = expenseBetween(transactions, visibleStart, visibleEnd.plusDays(1));
            result.add(new PeriodExpense(visibleStart.getMonthValue() + "/" + visibleStart.getDayOfMonth() + "～"
                    + visibleEnd.getMonthValue() + "/" + visibleEnd.getDayOfMonth(), amount));
            weekStart = weekStart.plusWeeks(1);
        }
        return result;
    }

    private List<PeriodExpense> monthlyExpenses(LocalDate base, List<DailyExpense> transactions) {
        List<PeriodExpense> result = new ArrayList<>();
        LocalDate yearStart = base.withDayOfYear(1);
        for (int month = 1; month <= 12; month++) {
            LocalDate monthStart = yearStart.withMonth(month);
            result.add(new PeriodExpense(month + "月", expenseBetween(transactions, monthStart, monthStart.plusMonths(1))));
        }
        return result;
    }

    private List<PeriodExpense> yearlyExpenses(LocalDate base, List<DailyExpense> transactions) {
        List<PeriodExpense> result = new ArrayList<>();
        for (int year = base.getYear() - 4; year <= base.getYear(); year++) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            result.add(new PeriodExpense(year + "年", expenseBetween(transactions, yearStart, yearStart.plusYears(1))));
        }
        return result;
    }

    private BigDecimal expenseBetween(List<DailyExpense> transactions, LocalDate from, LocalDate to) {
        return transactions.stream()
                .filter(item -> !item.date().isBefore(from) && item.date().isBefore(to))
                .map(DailyExpense::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private DateRange range(String value, LocalDate base, LocalDate from, LocalDate to) {
        String period = value == null ? "MONTH" : value.toUpperCase();
        return switch (period) {
            case "WEEK" -> new DateRange(base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), base.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
            case "YEAR" -> new DateRange(base.withDayOfYear(1), base.withDayOfYear(1).plusYears(1));
            case "CUSTOM" -> customRange(from, to);
            default -> new DateRange(base.withDayOfMonth(1), base.withDayOfMonth(1).plusMonths(1));
        };
    }
    private DateRange customRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw new IllegalArgumentException("自定义报表需要开始和结束日期");
        if (to.isBefore(from)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        return new DateRange(from, to.plusDays(1));
    }
    private BigDecimal sum(List<FinanceTransaction> transactions, TransactionDirection direction) { return transactions.stream().filter(this::includedInStatistics).filter(item -> item.getDirection() == direction).map(FinanceTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private boolean includedInStatistics(FinanceTransaction transaction) { return !IGNORED_CATEGORY.equals(transaction.getCategory()); }
    private String blankAs(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private record DateRange(LocalDate from, LocalDate to) { }
    private record DailyExpense(LocalDate date, BigDecimal amount) { }
    public record PeriodExpense(String label, BigDecimal amount) { }
    public record Report(String period, LocalDate from, LocalDate to, BigDecimal income, BigDecimal expense, BigDecimal balance,
                         Map<String, BigDecimal> expenseByCategory, List<PeriodExpense> expenseByPeriod,
                         List<FinanceTransaction> transactions) { }
}
