package com.java.dev.jim.personal.web;

import com.java.dev.jim.personal.domain.*;
import com.java.dev.jim.personal.service.CategoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
public class CategoryController {
    private final CategoryService service;
    public CategoryController(CategoryService service) { this.service = service; }
    @GetMapping("/categories") public List<Category> categories() { return service.categories(); }
    @PostMapping("/categories") public Category create(@RequestBody CategoryService.CategoryRequest request) { return service.createCategory(request); }
    @PutMapping("/categories/{id}") public Category update(@PathVariable Long id, @RequestBody CategoryService.CategoryRequest request) { return service.updateCategory(id, request); }
    @DeleteMapping("/categories/{id}") public CategoryService.DeleteResult delete(@PathVariable Long id) { return service.deleteCategory(id); }
    @GetMapping("/category-rules") public List<CategoryRule> rules() { return service.rules(); }
    @PostMapping("/category-rules") public CategoryRule createRule(@RequestBody CategoryService.RuleRequest request) { return service.createRule(request); }
    @PutMapping("/category-rules/{id}") public CategoryRule updateRule(@PathVariable Long id, @RequestBody CategoryService.RuleRequest request) { return service.updateRule(id, request); }
    @DeleteMapping("/category-rules/{id}") public ResponseEntity<Void> deleteRule(@PathVariable Long id) { service.deleteRule(id); return ResponseEntity.noContent().build(); }
    @PutMapping("/transactions/{id}/category") public CategoryService.AssignmentResult assign(@PathVariable Long id, @RequestBody AssignmentRequest request) { return service.assignManual(id, request.categoryId(), request.syncSimilar()); }
    @PostMapping("/categories/recategorize") public Map<String, Integer> recategorize() { return Map.of("updated", service.recategorize()); }
    public record AssignmentRequest(Long categoryId, boolean syncSimilar) { }
}
