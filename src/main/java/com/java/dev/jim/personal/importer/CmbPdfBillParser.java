package com.java.dev.jim.personal.importer;

import com.java.dev.jim.personal.domain.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.*;

@Component
public class CmbPdfBillParser implements BillParser {
    private static final Pattern DATE = Pattern.compile("(20\\d{2}[-/]\\d{1,2}[-/]\\d{1,2})(?:\\s+(\\d{1,2}:\\d{2}(?::\\d{2})?))?");
    private static final Pattern MONEY = Pattern.compile("[-+]?(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d{2})");
    @Override public BillSource source() { return BillSource.CMB; }
    @Override public boolean supports(Path file) { return file.getFileName().toString().contains("招商银行") && file.toString().endsWith(".pdf"); }
    @Override public List<ParsedTransaction> parse(Path file) throws IOException {
        String text;
        try (var document = Loader.loadPDF(file.toFile())) { text = new PDFTextStripper().getText(document); }
        if (text.isBlank()) throw new IOException("招商银行 PDF 没有可提取文本；当前版本未启用 OCR，请提供文本型 PDF 或安装 OCR 后重试");
        List<ParsedTransaction> result = new ArrayList<>();
        for (String line : text.lines().toList()) {
            Matcher date = DATE.matcher(line); if (!date.find()) continue;
            List<String> amounts = new ArrayList<>(); Matcher money = MONEY.matcher(line);
            while (money.find()) amounts.add(money.group());
            if (amounts.isEmpty()) continue;
            String time = date.group(2);
            LocalDateTime occurredAt = ParserSupport.date(date.group(1).replace('/', '-') + (time == null ? "" : " " + time));
            String amountValue = amounts.get(0); BigDecimal amount = ParserSupport.amount(amountValue);
            TransactionDirection direction = amountValue.startsWith("-") ? TransactionDirection.EXPENSE : TransactionDirection.INCOME;
            String summary = line.replace(date.group(), "").replaceAll(MONEY.pattern(), "").replaceAll("\\s+", " ").trim();
            direction = TransactionDirectionNormalizer.normalize(source(), direction, "", summary);
            result.add(new ParsedTransaction(source(), "", ParserSupport.fingerprint(source(), "招商银行", occurredAt, direction, amount, "", summary),
                    occurredAt, direction, amount, "招商银行", "", summary, "未分类", "{}"));
        }
        if (result.isEmpty()) throw new IOException("未能从招商银行 PDF 识别出交易记录；请检查账单格式或启用 OCR");
        return result;
    }
}
