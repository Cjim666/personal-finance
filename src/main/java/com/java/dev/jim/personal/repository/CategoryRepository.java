package com.java.dev.jim.personal.repository;

import com.java.dev.jim.personal.domain.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
    List<Category> findAllByOrderBySortOrderAscNameAsc();
}
