package com.java.dev.jim.personal.importer;

import com.java.dev.jim.personal.domain.BillSource;
import com.java.dev.jim.personal.domain.TransactionDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionDirectionNormalizerTests {
    @Test void classifiesConfirmedRepaymentsAsNeutral() {
        assertEquals(TransactionDirection.NEUTRAL, TransactionDirectionNormalizer.normalize(
                BillSource.CMB, TransactionDirection.EXPENSE, "", "CNY 信用卡自动还款 陈光进"));
        assertEquals(TransactionDirection.NEUTRAL, TransactionDirectionNormalizer.normalize(
                BillSource.ALIPAY, TransactionDirection.EXPENSE, "支付宝", "花呗还款"));
    }

    @Test void keepsNormalHuabeiPurchasesAsExpense() {
        assertEquals(TransactionDirection.EXPENSE, TransactionDirectionNormalizer.normalize(
                BillSource.ALIPAY, TransactionDirection.EXPENSE, "支付宝", "花呗消费"));
    }
}
