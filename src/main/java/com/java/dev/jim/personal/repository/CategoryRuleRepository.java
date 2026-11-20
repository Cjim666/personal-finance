package com.java.dev.jim.personal.repository;

import com.java.dev.jim.personal.domain.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {
    List<CategoryRule> findAllByOrderByPriorityDescIdAsc();
    long countByCategoryId(Long categoryId);
    long deleteByCategoryId(Long categoryId);
}
