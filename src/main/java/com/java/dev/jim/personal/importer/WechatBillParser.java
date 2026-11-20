package com.java.dev.jim.personal.importer;

import com.java.dev.jim.personal.domain.*;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import java.io.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class WechatBillParser implements BillParser {
    @Override public BillSource source() { return BillSource.WECHAT; }
    @Override public boolean supports(Path file) { String name = file.getFileName().toString(); return name.contains("微信") && (name.endsWith(".xlsx") || name.endsWith(".xls")); }
    @Override public List<ParsedTransaction> parse(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file); Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0); DataFormatter formatter = new DataFormatter();
            Map<String, Integer> headers = null; List<ParsedTransaction> result = new ArrayList<>();
            for (Row row : sheet) {
                if (headers == null) {
                    Map<String, Integer> candidate = new LinkedHashMap<>();
                    for (Cell cell : row) candidate.put(ParserSupport.normalizeHeader(formatter.formatCellValue(cell)), cell.getColumnIndex());
                    if (candidate.keySet().stream().anyMatch(header -> header.contains("交易时间"))) headers = candidate;
                    continue;
                }
                Map<String, String> values = row(headers, row, formatter);
                String state = ParserSupport.cell(values, "当前状态", "交易状态");
                String directionValue = ParserSupport.cell(values, "收/支", "收支");
                if (!state.contains("支付成功") || directionValue.contains("不计收支") || directionValue.isBlank()) continue;
                LocalDateTime occurredAt = ParserSupport.date(ParserSupport.cell(values, "交易时间"));
                BigDecimal amount = ParserSupport.amount(ParserSupport.cell(values, "金额(元)", "金额（元）", "金额"));
                TransactionDirection direction = ParserSupport.direction(directionValue);
                String externalId = ParserSupport.cell(values, "交易单号");
                String counterparty = ParserSupport.cell(values, "交易对方");
                String summary = ParserSupport.cell(values, "商品", "备注");
                direction = TransactionDirectionNormalizer.normalize(source(), direction, counterparty, summary);
                result.add(new ParsedTransaction(source(), externalId,
                        ParserSupport.fingerprint(source(), "微信支付", occurredAt, direction, amount, counterparty, summary), occurredAt,
                        direction, amount, "微信支付", counterparty, summary, "未分类", ParserSupport.json(values)));
            }
            if (headers == null) throw new IllegalArgumentException("未找到微信账单表头");
            return result;
        } catch (Exception exception) {
            if (exception instanceof IOException ioException) throw ioException;
            throw new IOException("微信账单解析失败：" + exception.getMessage(), exception);
        }
    }
    private Map<String, String> row(Map<String, Integer> headers, Row row, DataFormatter formatter) {
        Map<String, String> values = new LinkedHashMap<>();
        headers.forEach((header, index) -> values.put(header, formatter.formatCellValue(row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim()));
        return values;
    }
}
