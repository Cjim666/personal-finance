package com.java.dev.jim.personal.importer;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BillParserSampleTests {
    private final Path pendingDirectory = Path.of("bill/pending");

    @Test void parsesAvailableWechatSample() throws Exception {
        assertParses(new WechatBillParser());
    }

    @Test void parsesAvailableAlipaySample() throws Exception {
        assertParses(new AlipayBillParser());
    }

    @Test void parsesAvailableCmbSample() throws Exception {
        assertParses(new CmbPdfBillParser());
    }

    private void assertParses(BillParser parser) throws Exception {
        Assumptions.assumeTrue(Files.isDirectory(pendingDirectory));
        Path sample;
        try (var files = Files.list(pendingDirectory)) {
            sample = files.filter(Files::isRegularFile).filter(parser::supports).findFirst().orElse(null);
        }
        Assumptions.assumeTrue(sample != null, "未提供对应账单样本");
        assertFalse(parser.parse(sample).isEmpty(), "账单解析结果不能为空");
    }
}
