package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.*;
import com.java.dev.jim.personal.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:optimization-tests;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=true"
})
@Transactional
class FinanceOptimizationTests {
    @Autowired FinanceTransactionRepository transactions;
    @Autowired ImportBatchRepository batches;
    @Autowired ReportService reports;
    @Autowired CategoryService categories;
    @Autowired List<com.java.dev.jim.personal.importer.BillParser> parsers;
    @TempDir Path directory;

    private void add(ImportBatch batch, String key, String direction, String amount, String category, String date) {
        transactions.saveAndFlush(new FinanceTransaction(batch, new ParsedTransaction(BillSource.ALIPAY, key, key,
                LocalDateTime.parse(date), TransactionDirection.valueOf(direction), new BigDecimal(amount),
                "synthetic", "synthetic", "synthetic", category, "{}")));
    }

    @Test void databaseTrendExcludesPendingNeutralIgnoredAndRespectsRange() {
        var confirmed = new ImportBatch(BillSource.ALIPAY, "synthetic.csv", "confirmed");
        confirmed.confirm(); batches.saveAndFlush(confirmed);
        var pending = batches.saveAndFlush(new ImportBatch(BillSource.ALIPAY, "pending.csv", "pending"));
        add(confirmed, "expense", "EXPENSE", "10.1234", "餐饮", "2026-10-01T00:00:00");
        add(confirmed, "null-category", "EXPENSE", "2.0001", null, "2026-10-31T23:59:59");
        add(confirmed, "ignored", "EXPENSE", "999", "忽略", "2026-10-01T01:00:00");
        add(confirmed, "neutral", "NEUTRAL", "999", "未分类", "2026-10-01T02:00:00");
        add(confirmed, "income", "INCOME", "20", "未分类", "2026-10-01T03:00:00");
        add(pending, "pending", "EXPENSE", "999", "餐饮", "2026-10-01T04:00:00");
        add(confirmed, "next-month", "EXPENSE", "50", "餐饮", "2026-11-01T00:00:00");
        var result = reports.report("MONTH", LocalDate.of(2026, 10, 1), null, null);
        assertEquals(0, new BigDecimal("12.1235").compareTo(result.expense()));
        assertEquals(0, result.expense().compareTo(result.expenseByPeriod().get(9).amount()));
        assertEquals(0, new BigDecimal("50").compareTo(result.expenseByPeriod().get(10).amount()));
        assertEquals(5, result.transactions().size());
        var snapshot = new AnalysisService.MonthlyAnalysis("2026-10", "test", "test", "2026-10-01", "2026-10-31",
                "test", "test", "test", 5,
                new AnalysisService.Totals(new BigDecimal("20.0"), new BigDecimal("12.1235"), new BigDecimal("7.8765"), null),
                null, null, List.of(new AnalysisService.CategoryComparison("餐饮", new BigDecimal("10.1234"), null, null, null),
                new AnalysisService.CategoryComparison("未分类", new BigDecimal("2.0001"), null, null, null),
                new AnalysisService.CategoryComparison("上月分类", BigDecimal.ZERO, null, null, null)), List.of());
        assertFalse(AnalysisService.status(snapshot, result).stale());
        add(confirmed, "new-entry", "EXPENSE", "1", "餐饮", "2026-10-02T00:00:00");
        assertTrue(AnalysisService.status(snapshot, reports.report("MONTH", LocalDate.of(2026, 10, 1), null, null)).stale());
    }

    @Test void failedUploadCanRetryButPartialFailureCannotBeRetried() throws Exception {
        var service = new BillImportService(directory.resolve("pending").toString(), parsers, batches, transactions, categories);
        byte[] content = "交易时间,交易状态,收/支,金额,支付宝交易号,交易对方,商品名称\n2026-10-01 12:00:00,交易成功,支出,12.34,synthetic-id,test,test\n"
                .getBytes(java.nio.charset.Charset.forName("GB18030"));
        String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        var failed = new ImportBatch(BillSource.ALIPAY, "支付宝测试.csv", hash);
        failed.fail("synthetic failure"); batches.saveAndFlush(failed);
        var file = new MockMultipartFile("files", "支付宝测试.csv", "text/csv", content);
        var retry = service.upload(List.of(file), null).getFirst();
        assertEquals("UPLOADED", retry.status());
        assertEquals(failed.getId(), retry.batch().id());
        assertEquals(1, retry.batch().totalCount());
        assertEquals(1, transactions.count());
        assertEquals("DUPLICATE", service.upload(List.of(file), null).getFirst().status());
        failed.fail("partial failure"); batches.saveAndFlush(failed);
        assertEquals("FAILED", service.upload(List.of(file), null).getFirst().status());
        assertEquals(1, transactions.count());
        assertEquals(0, reports.report("MONTH", LocalDate.of(2026, 10, 1), null, null).expense().signum());
    }
}
