package com.java.dev.jim.personal.service;

import com.java.dev.jim.personal.domain.*;
import com.java.dev.jim.personal.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class CategoryService {
    private static final Set<String> SYSTEM_CATEGORIES = Set.of("未分类", "忽略");
    private final CategoryRepository categories;
    private final CategoryRuleRepository rules;
    private final FinanceTransactionRepository transactions;

    public CategoryService(CategoryRepository categories, CategoryRuleRepository rules, FinanceTransactionRepository transactions) {
        this.categories = categories; this.rules = rules; this.transactions = transactions;
    }
    public List<Category> categories() { return categories.findAllByOrderBySortOrderAscNameAsc(); }
    public List<CategoryRule> rules() { return rules.findAllByOrderByPriorityDescIdAsc(); }
    @Transactional public Category createCategory(CategoryRequest request) {
        String name = required(request.name(), "分类名称");
        if (categories.findByName(name).isPresent()) throw new IllegalArgumentException("分类已存在：" + name);
        return categories.save(new Category(name, request.active(), request.sortOrder()));
    }
    @Transactional public Category updateCategory(Long id, CategoryRequest request) {
        Category category = category(id); String name = required(request.name(), "分类名称");
        if (SYSTEM_CATEGORIES.contains(category.getName()) && (!category.getName().equals(name) || !request.active()))
            throw new IllegalStateException("系统分类不能改名或停用：" + category.getName());
        categories.findByName(name).filter(found -> !found.getId().equals(id)).ifPresent(found -> { throw new IllegalArgumentException("分类已存在：" + name); });
        category.update(name, request.active(), request.sortOrder()); return categories.save(category);
    }
    @Transactional public DeleteResult deleteCategory(Long id) {
        Category target = category(id);
        if (SYSTEM_CATEGORIES.contains(target.getName())) throw new IllegalStateException("系统分类不能删除：" + target.getName());
        int movedTransactions = transactions.moveCategoryTransactionsToUnclassified(id);
        long deletedRules = rules.deleteByCategoryId(id);
        categories.delete(target);
        return new DeleteResult(movedTransactions, deletedRules);
    }
    @Transactional public CategoryRule createRule(RuleRequest request) { return rules.save(rule(request, null)); }
    @Transactional public CategoryRule updateRule(Long id, RuleRequest request) {
        CategoryRule rule = rules.findById(id).orElseThrow(() -> new NoSuchElementException("分类规则不存在"));
        RuleRequest value = validatedRule(request); rule.update(category(value.categoryId()), value.source(), value.keyword(), value.priority(), value.active()); return rules.save(rule);
    }
    @Transactional public void deleteRule(Long id) { rules.delete(rules.findById(id).orElseThrow(() -> new NoSuchElementException("分类规则不存在"))); }
    @Transactional public AssignmentResult assignManual(Long transactionId, Long categoryId, boolean syncSimilar) {
        FinanceTransaction transaction = transactions.findById(transactionId).orElseThrow(() -> new NoSuchElementException("交易不存在"));
        Category selected = category(categoryId);
        boolean shouldCreateRule = syncSimilar && transaction.getDirection() == TransactionDirection.EXPENSE && transaction.getCategoryOrigin() == CategoryOrigin.UNCLASSIFIED;
        String keyword = shouldCreateRule ? automaticKeyword(transaction) : null;
        transaction.setCategory(selected, CategoryOrigin.MANUAL);
        transactions.save(transaction);
        if (keyword == null) return new AssignmentResult(null, false, 0);

        boolean ruleCreated = ensureAutomaticRule(selected, transaction.getSource(), keyword);
        int updated = 0;
        for (FinanceTransaction candidate : transactions.findBySourceAndCategoryOriginAndDirection(transaction.getSource(), CategoryOrigin.UNCLASSIFIED, TransactionDirection.EXPENSE)) {
            if (!matches(candidate, keyword)) continue;
            candidate.setCategory(selected, CategoryOrigin.AUTOMATIC);
            updated++;
        }
        return new AssignmentResult(keyword, ruleCreated, updated);
    }
    @Transactional public int recategorize() {
        int changed = 0;
        for (FinanceTransaction transaction : transactions.findByCategoryOriginNot(CategoryOrigin.MANUAL)) {
            if (transaction.getDirection() != TransactionDirection.EXPENSE) continue;
            Category match = match(transaction.getSource(), transaction.getCounterparty(), transaction.getSummary());
            transaction.setCategory(match, match == null ? CategoryOrigin.UNCLASSIFIED : CategoryOrigin.AUTOMATIC); changed++;
        }
        return changed;
    }
    public void applyAutomaticCategory(FinanceTransaction transaction) {
        if (transaction.getDirection() != TransactionDirection.EXPENSE) return;
        Category match = match(transaction.getSource(), transaction.getCounterparty(), transaction.getSummary());
        transaction.setCategory(match, match == null ? CategoryOrigin.UNCLASSIFIED : CategoryOrigin.AUTOMATIC);
    }
    private CategoryRule rule(RuleRequest request, Long ignored) { RuleRequest value = validatedRule(request); return new CategoryRule(category(value.categoryId()), value.source(), value.keyword(), value.priority(), value.active()); }
    private RuleRequest validatedRule(RuleRequest request) {
        if (request == null || (request.source() == null && (request.keyword() == null || request.keyword().isBlank()))) throw new IllegalArgumentException("规则至少指定账单来源或关键词");
        return request;
    }
    private Category match(BillSource source, String counterparty, String summary) {
        String content = ((counterparty == null ? "" : counterparty) + " " + (summary == null ? "" : summary)).toLowerCase(Locale.ROOT);
        for (CategoryRule rule : rules.findAllByOrderByPriorityDescIdAsc()) if (rule.isActive() && rule.getCategory().isActive()
                && (rule.getSource() == null || rule.getSource() == source)
                && (rule.getKeyword() == null || rule.getKeyword().isBlank() || content.contains(rule.getKeyword().toLowerCase(Locale.ROOT)))) return rule.getCategory();
        return null;
    }
    private boolean ensureAutomaticRule(Category category, BillSource source, String keyword) {
        boolean exists = rules.findAllByOrderByPriorityDescIdAsc().stream().anyMatch(rule -> rule.getSource() == source
                && keyword.equalsIgnoreCase(rule.getKeyword()));
        if (!exists) rules.save(new CategoryRule(category, source, keyword, 200, true));
        return !exists;
    }
    private String automaticKeyword(FinanceTransaction transaction) {
        if (transaction.getCounterparty() != null && !transaction.getCounterparty().isBlank()) return transaction.getCounterparty().trim();
        if (transaction.getSummary() != null && !transaction.getSummary().isBlank()) return transaction.getSummary().trim();
        return null;
    }
    private boolean matches(FinanceTransaction transaction, String keyword) {
        String content = ((transaction.getCounterparty() == null ? "" : transaction.getCounterparty()) + " "
                + (transaction.getSummary() == null ? "" : transaction.getSummary())).toLowerCase(Locale.ROOT);
        return content.contains(keyword.toLowerCase(Locale.ROOT));
    }
    private Category category(Long id) { return categories.findById(id).orElseThrow(() -> new NoSuchElementException("分类不存在")); }
    private String required(String value, String label) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空"); return value.trim(); }
    public record CategoryRequest(String name, boolean active, int sortOrder) { }
    public record RuleRequest(Long categoryId, BillSource source, String keyword, int priority, boolean active) { }
    public record AssignmentResult(String keyword, boolean ruleCreated, int autoUpdated) { }
    public record DeleteResult(int movedTransactions, long deletedRules) { }
}
