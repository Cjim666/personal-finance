package com.java.dev.jim.personal.importer;

import com.java.dev.jim.personal.domain.BillSource;
import com.java.dev.jim.personal.domain.TransactionDirection;

import java.util.Locale;

final class TransactionDirectionNormalizer {
    private TransactionDirectionNormalizer() { }

    static TransactionDirection normalize(BillSource source, TransactionDirection direction, String counterparty, String summary) {
        if (direction != TransactionDirection.EXPENSE) return direction;
        String content = ((counterparty == null ? "" : counterparty) + " " + (summary == null ? "" : summary)).toLowerCase(Locale.ROOT);
        if (content.contains("信用卡自动还款") || content.contains("信用卡还款")) return TransactionDirection.NEUTRAL;
        if (source == BillSource.CMB && (content.contains("支付宝信贷业务待还款账户") || content.contains("银联快捷支付 还款"))) return TransactionDirection.NEUTRAL;
        if (source == BillSource.ALIPAY && content.contains("花呗") && content.contains("还款")) return TransactionDirection.NEUTRAL;
        return direction;
    }
}
