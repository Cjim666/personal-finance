package com.java.dev.jim.personal.web;

import com.java.dev.jim.personal.service.BillImportService;
import com.java.dev.jim.personal.service.ReportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FinanceControllerTests {
    @Test void uploadPassesAllAttachmentsToImportService() throws Exception {
        BillImportService importService = mock(BillImportService.class);
        FinanceController controller = new FinanceController(importService, mock(ReportService.class));
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "支付宝账单.csv", "text/csv", "first".getBytes()),
                new MockMultipartFile("files", "招商银行账单.pdf", "application/pdf", "second".getBytes()));
        List<BillImportService.UploadSummary> expected = List.of();
        when(importService.upload(files, null)).thenReturn(expected);

        var result = controller.upload(files, null);

        assertEquals(expected, result);
        verify(importService).upload(files, null);
    }
}
