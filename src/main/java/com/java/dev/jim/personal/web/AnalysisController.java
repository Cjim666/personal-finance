package com.java.dev.jim.personal.web;

import com.java.dev.jim.personal.service.AnalysisService;
import com.java.dev.jim.personal.service.ReportService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {
    private final AnalysisService service;
    private final ReportService reports;

    public AnalysisController(AnalysisService service, ReportService reports) { this.service = service; this.reports = reports; }

    @GetMapping("/{month}/status")
    public AnalysisService.SnapshotStatus status(@PathVariable String month) {
        var snapshot = service.read(month);
        var start = java.time.YearMonth.parse(month).atDay(1);
        var current = reports.report("CUSTOM", start, start, start.plusMonths(1).minusDays(1));
        return AnalysisService.status(snapshot, current);
    }

    @GetMapping
    public List<AnalysisService.AnalysisSummary> list() { return service.list(); }

    @GetMapping("/{month}")
    public AnalysisService.MonthlyAnalysis read(@PathVariable String month) { return service.read(month); }
}
