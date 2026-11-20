package com.java.dev.jim.personal.importer;

import com.java.dev.jim.personal.domain.*;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class AlipayBillParser implements BillParser {
    @Override public BillSource source() { return BillSource.ALIPAY; }
    @Override public boolean supports(Path file) { return file.getFileName().toString().contains("支付宝") && file.toString().endsWith(".csv"); }
    @Override public List<ParsedTransaction> parse(Path file) throws IOException {
        List<ParsedTransaction> result = new ArrayList<>();
        for (Map<String, String> row : CsvRows.read(file, Charset.forName("GB18030"), "交易时间")) {
            String status = ParserSupport.cell(row, "交易状态");
            String directionValue = ParserSupport.cell(row, "收/支", "收支");
            if (!status.contains("成功") || directionValue.contains("不计收支") || directionValue.isBlank()) continue;
            LocalDateTime occurredAt = ParserSupport.date(ParserSupport.cell(row, "交易时间", "交易创建时间", "付款时间"));
            BigDecimal amount = ParserSupport.amount(ParserSupport.cell(row, "金额（元）", "金额"));
            TransactionDirection direction = ParserSupport.direction(directionValue);
            String externalId = ParserSupport.cell(row, "支付宝交易号", "交易订单号", "交易号");
            String counterparty = ParserSupport.cell(row, "交易对方");
            String summary = ParserSupport.cell(row, "商品名称", "备注");
            direction = TransactionDirectionNormalizer.normalize(source(), direction, counterparty, summary);
            result.add(new ParsedTransaction(source(), externalId,
                    ParserSupport.fingerprint(source(), "支付宝", occurredAt, direction, amount, counterparty, summary), occurredAt,
                    direction, amount, "支付宝", counterparty, summary, "未分类", ParserSupport.json(row)));
        }
        return result;
    }
}
