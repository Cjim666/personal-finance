package com.java.dev.jim.personal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisServiceTests {
    @TempDir Path directory;

    private AnalysisService service() { return new AnalysisService(directory.toString(), JsonMapper.builder().build()); }

    private String snapshot(String month, String overview) {
        return """
                {"month":"%s","title":"月度分析","generatedAt":"2026-11-06 08:00 +08:00",
                "coverage":"账单完整性未确认","overview":"%s","confirmedTransactionCount":2,
                "current":{"income":"100.1000","expense":"30.0200","balance":"70.0800","balanceRate":"70.01"},
                "sections":[],"categories":[]}
                """.formatted(month, overview);
    }

    @Test void snapshotsSurviveNewServiceAndSameMonthReplacement() throws Exception {
        Files.writeString(directory.resolve("2026-08.json"), snapshot("2026-08", "旧总结"));
        Files.writeString(directory.resolve("2026-07.json"), snapshot("2026-07", "七月总结"));
        assertEquals(new BigDecimal("70.0800"), service().read("2026-08").current().balance());
        Files.writeString(directory.resolve("2026-08.json"), snapshot("2026-08", "新总结"));
        assertEquals("新总结", service().read("2026-08").overview());
        assertEquals("七月总结", service().read("2026-07").overview());
        assertEquals(java.util.List.of("2026-08", "2026-07"), service().list().stream().map(AnalysisService.AnalysisSummary::month).toList());
    }

    @Test void missingDirectoryIsEmptyAndMissingMonthIsNotFound() {
        assertTrue(new AnalysisService(directory.resolve("missing").toString(), JsonMapper.builder().build()).list().isEmpty());
        assertEquals(404, assertThrows(ResponseStatusException.class, () -> service().read("2026-08")).getStatusCode().value());
    }

    @Test void rejectsInvalidMonthsTraversalAndDamagedSnapshots() throws Exception {
        for (String month : java.util.List.of("../2026-08", "2026-13", "26-08"))
            assertEquals(400, assertThrows(ResponseStatusException.class, () -> service().read(month)).getStatusCode().value());
        Files.writeString(directory.resolve("2026-08.json"), "{broken}");
        assertEquals(500, assertThrows(ResponseStatusException.class, () -> service().read("2026-08")).getStatusCode().value());
        Files.writeString(directory.resolve("2026-08.json"), snapshot("2026-07", "错误月份"));
        assertEquals(500, assertThrows(ResponseStatusException.class, () -> service().read("2026-08")).getStatusCode().value());
    }
}
