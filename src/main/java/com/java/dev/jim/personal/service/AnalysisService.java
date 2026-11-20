package com.java.dev.jim.personal.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/** Reads permanent monthly analysis snapshots independently of the financial database. */
@Service
public class AnalysisService {
    private final Path directory;
    private final ObjectMapper mapper;

    public AnalysisService(@Value("${app.analysis.directory:./data/analyses}") String directory, ObjectMapper mapper) {
        this.directory = Path.of(directory);
        this.mapper = mapper;
    }

    public List<AnalysisSummary> list() {
        if (!Files.exists(directory)) return List.of();
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d{4}-\\d{2}\\.json"))
                    .map(name -> read(name.substring(0, 7)))
                    .map(item -> new AnalysisSummary(item.month(), item.title(), item.generatedAt(), item.coverage()))
                    .sorted(Comparator.comparing(AnalysisSummary::month).reversed()).toList();
        } catch (IOException exception) {
            throw unavailable();
        }
    }

    public MonthlyAnalysis read(String month) {
        if (!month.matches("\\d{4}-\\d{2}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月份格式应为 YYYY-MM");
        try {
            YearMonth.parse(month);
        } catch (java.time.DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "月份无效");
        }
        Path file = directory.resolve(month + ".json");
        if (!Files.exists(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "该月份尚未保存总结");
        try {
            MonthlyAnalysis analysis = mapper.readValue(Files.readString(file), MonthlyAnalysis.class);
            if (!month.equals(analysis.month()) || analysis.current() == null || analysis.sections() == null
                    || analysis.categories() == null || analysis.generatedAt() == null || analysis.coverage() == null) throw unavailable();
            return analysis;
        } catch (IOException | tools.jackson.core.JacksonException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "月度总结文件无法读取，请检查本地归档");
    }

    public static SnapshotStatus status(MonthlyAnalysis snapshot, ReportService.Report current) {
        boolean changed = snapshot.confirmedTransactionCount() != current.transactions().size()
                || snapshot.current().income().compareTo(current.income()) != 0
                || snapshot.current().expense().compareTo(current.expense()) != 0;
        var savedCategories = new java.util.TreeMap<String, BigDecimal>();
        snapshot.categories().forEach(row -> savedCategories.put(row.category(), row.amount()));
        var names = new java.util.HashSet<>(savedCategories.keySet());
        names.addAll(current.expenseByCategory().keySet());
        for (String name : names) {
            changed |= savedCategories.getOrDefault(name, BigDecimal.ZERO)
                    .compareTo(current.expenseByCategory().getOrDefault(name, BigDecimal.ZERO)) != 0;
        }
        var dates = current.transactions().stream().filter(t -> !"忽略".equals(t.getCategory()))
                .map(t -> t.getOccurredAt().toLocalDate().toString()).sorted().toList();
        if (!dates.isEmpty()) changed |= !dates.getFirst().equals(snapshot.dataFrom()) || !dates.getLast().equals(snapshot.dataThrough());
        return new SnapshotStatus(changed, changed ? "账务数据已变化，建议重新总结本月。当前仍展示原归档。"
                : "收支、分类、笔数与日期覆盖和当前账务一致；此检查不核对逐笔交易内容。");
    }

    public record SnapshotStatus(boolean stale, String message) { }

    public record AnalysisSummary(String month, String title, String generatedAt, String coverage) { }
    public record Totals(BigDecimal income, BigDecimal expense, BigDecimal balance, BigDecimal balanceRate) { }
    public record CategoryComparison(String category, BigDecimal amount, BigDecimal share,
                                     BigDecimal previousAmount, BigDecimal change) { }
    public record Section(String heading, String text) { }
    public record MonthlyAnalysis(String month, String title, String generatedAt, String dataFrom, String dataThrough,
                                  String coverage, String basis, String overview, int confirmedTransactionCount,
                                  Totals current, Totals previous, BigDecimal expenseChangeRate,
                                  List<CategoryComparison> categories, List<Section> sections) { }
}
