package com.java.dev.jim.personal.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillDirectoryScheduler {
    private final BillImportService importService;
    public BillDirectoryScheduler(BillImportService importService) { this.importService = importService; }

    @Scheduled(initialDelay = 5_000, fixedDelayString = "${app.bill.scan-delay-ms:300000}")
    public void scanPendingDirectory() {
        try {
            importService.scan();
        } catch (Exception ignored) {
            // 单个账单的失败会写入导入批次；调度器不记录原始账单内容。
        }
    }
}
