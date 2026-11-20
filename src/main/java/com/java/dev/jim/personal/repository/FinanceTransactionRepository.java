package com.java.dev.jim.personal.repository;

import com.java.dev.jim.personal.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface FinanceTransactionRepository extends JpaRepository<FinanceTransaction, Long> {
    interface DailyExpense {
        Integer getYear();
        Integer getMonth();
        Integer getDay();
        java.math.BigDecimal getAmount();
    }

    @Query("select year(t.occurredAt) as year, month(t.occurredAt) as month, day(t.occurredAt) as day, sum(t.amount) as amount " +
            "from FinanceTransaction t where t.importBatch.status = :status and t.direction = com.java.dev.jim.personal.domain.TransactionDirection.EXPENSE " +
            "and (t.category is null or t.category <> '忽略') and t.occurredAt >= :from and t.occurredAt < :to " +
            "group by year(t.occurredAt), month(t.occurredAt), day(t.occurredAt)")
    List<DailyExpense> dailyExpenses(@Param("status") ImportBatchStatus status,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    boolean existsBySourceAndExternalId(BillSource source, String externalId);
    boolean existsByBusinessFingerprint(String businessFingerprint);
    boolean existsByImportBatchId(Long importBatchId);
    long countByCategoryDefinitionId(Long categoryId);
    @Modifying
    @Query("update FinanceTransaction transaction set transaction.categoryDefinition = null, transaction.category = '未分类', " +
            "transaction.categoryOrigin = com.java.dev.jim.personal.domain.CategoryOrigin.UNCLASSIFIED " +
            "where transaction.categoryDefinition.id = :categoryId")
    int moveCategoryTransactionsToUnclassified(@Param("categoryId") Long categoryId);
    List<FinanceTransaction> findByCategoryOriginNot(CategoryOrigin categoryOrigin);
    List<FinanceTransaction> findBySourceAndCategoryOriginAndDirection(BillSource source, CategoryOrigin categoryOrigin, TransactionDirection direction);
    List<FinanceTransaction> findByImportBatchStatusAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            ImportBatchStatus status, LocalDateTime from, LocalDateTime to);
}
