package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateReviewServiceTests {
    private FinanceTransaction entry(BillSource source, String key, String amount, String direction, String category) {
        return new FinanceTransaction(new ImportBatch(source, "synthetic", key), new ParsedTransaction(source, key, key,
                LocalDateTime.parse("2026-11-01T10:00:00"), TransactionDirection.valueOf(direction), new BigDecimal(amount),
                "synthetic", "synthetic", "synthetic", category, "{}"));
    }
    @Test void sameContentIncludesNeutralAndCrossSourceUsesExactDecimal() {
        var service = new DuplicateReviewService();
        var candidates = service.review(List.of(entry(BillSource.CMB,"a","12.3400","NEUTRAL","未分类"),
                entry(BillSource.CMB,"b","12.34","NEUTRAL","未分类"),
                entry(BillSource.ALIPAY,"c","10.0000","EXPENSE","餐饮"),
                entry(BillSource.CMB,"d","10","EXPENSE","餐饮"),
                entry(BillSource.WECHAT,"e","10.0001","EXPENSE","餐饮"),
                entry(BillSource.WECHAT,"f","10","EXPENSE","忽略")));
        assertEquals(List.of("SAME_CONTENT", "CROSS_SOURCE_SAME_DAY_AMOUNT"), candidates.stream().map(DuplicateReviewService.Candidate::reason).toList());
        assertEquals(2, candidates.get(1).entries().size());
    }
    @Test void unrelatedSameSourceAmountsAreNotCrossSourceCandidates() {
        assertTrue(new DuplicateReviewService().review(List.of(entry(BillSource.ALIPAY,"a","10","INCOME","餐饮"),
                entry(BillSource.CMB,"b","10","EXPENSE","餐饮"))).isEmpty());
    }
}
