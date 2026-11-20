package com.java.dev.jim.personal.web;

import com.java.dev.jim.personal.service.CategoryService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CategoryControllerTests {
    @Test void assignCategoryReturnsAutomaticProcessingResult() {
        CategoryService service = mock(CategoryService.class);
        CategoryController controller = new CategoryController(service);
        CategoryService.AssignmentResult result = new CategoryService.AssignmentResult("示例商户", true, 4);
        when(service.assignManual(8L, 3L, true)).thenReturn(result);

        var response = controller.assign(8L, new CategoryController.AssignmentRequest(3L, true));

        assertEquals(result, response);
        verify(service).assignManual(8L, 3L, true);
    }
}
