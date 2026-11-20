package com.java.dev.jim.personal.web;

import com.java.dev.jim.personal.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class DuplicateReviewController {
    private final ReportService reports;
    private final DuplicateReviewService reviews;
    public DuplicateReviewController(ReportService reports, DuplicateReviewService reviews) {
        this.reports = reports; this.reviews = reviews;
    }
    @GetMapping("/duplicates")
    public List<DuplicateReviewService.Candidate> duplicates(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusYears(1)))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                    "核对范围须为正序且不超过一年");
        return reviews.review(reports.report("CUSTOM", from, from, to).transactions());
    }
}
