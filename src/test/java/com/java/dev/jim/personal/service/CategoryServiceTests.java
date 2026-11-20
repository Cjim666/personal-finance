package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.*;
import com.java.dev.jim.personal.repository.CategoryRepository;
import com.java.dev.jim.personal.repository.CategoryRuleRepository;
import com.java.dev.jim.personal.repository.FinanceTransactionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CategoryServiceTests {
    @Test void deleteCategoryMovesTransactionsToUnclassifiedAndDeletesRules() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryRuleRepository rules = mock(CategoryRuleRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        CategoryService service = new CategoryService(categories, rules, transactions);
        Category target = new Category("临时分类", true, 100);
        when(categories.findById(8L)).thenReturn(Optional.of(target));
        when(transactions.moveCategoryTransactionsToUnclassified(8L)).thenReturn(3);
        when(rules.deleteByCategoryId(8L)).thenReturn(2L);

        var result = service.deleteCategory(8L);

        assertEquals(new CategoryService.DeleteResult(3, 2), result);
        verify(categories).delete(target);
    }

    @Test void manualCategoryCreatesRuleAndUpdatesSimilarUnclassifiedExpenses() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryRuleRepository rules = mock(CategoryRuleRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        CategoryService service = new CategoryService(categories, rules, transactions);
        Category target = new Category("餐饮", true, 10);
        FinanceTransaction selected = mock(FinanceTransaction.class);
        FinanceTransaction similar = mock(FinanceTransaction.class);

        when(transactions.findById(1L)).thenReturn(Optional.of(selected));
        when(categories.findById(2L)).thenReturn(Optional.of(target));
        when(selected.getDirection()).thenReturn(TransactionDirection.EXPENSE);
        when(selected.getCategoryOrigin()).thenReturn(CategoryOrigin.UNCLASSIFIED);
        when(selected.getSource()).thenReturn(BillSource.WECHAT);
        when(selected.getCounterparty()).thenReturn("示例餐厅");
        when(rules.findAllByOrderByPriorityDescIdAsc()).thenReturn(List.of());
        when(transactions.findBySourceAndCategoryOriginAndDirection(BillSource.WECHAT, CategoryOrigin.UNCLASSIFIED, TransactionDirection.EXPENSE)).thenReturn(List.of(similar));
        when(similar.getCounterparty()).thenReturn("示例餐厅（分店）");

        CategoryService.AssignmentResult result = service.assignManual(1L, 2L, true);

        assertEquals(new CategoryService.AssignmentResult("示例餐厅", true, 1), result);
        verify(selected).setCategory(target, CategoryOrigin.MANUAL);
        verify(similar).setCategory(target, CategoryOrigin.AUTOMATIC);
        verify(rules).save(any(CategoryRule.class));
    }

    @Test void manualCategoryWithoutSyncOnlyUpdatesSelectedTransaction() {
        CategoryRepository categories = mock(CategoryRepository.class);
        CategoryRuleRepository rules = mock(CategoryRuleRepository.class);
        FinanceTransactionRepository transactions = mock(FinanceTransactionRepository.class);
        CategoryService service = new CategoryService(categories, rules, transactions);
        Category target = new Category("餐饮", true, 10);
        FinanceTransaction selected = mock(FinanceTransaction.class);

        when(transactions.findById(1L)).thenReturn(Optional.of(selected));
        when(categories.findById(2L)).thenReturn(Optional.of(target));

        CategoryService.AssignmentResult result = service.assignManual(1L, 2L, false);

        assertEquals(new CategoryService.AssignmentResult(null, false, 0), result);
        verify(selected).setCategory(target, CategoryOrigin.MANUAL);
        verify(rules, never()).save(any(CategoryRule.class));
        verify(transactions, never()).findBySourceAndCategoryOriginAndDirection(any(), any(), any());
    }
}
