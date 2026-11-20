package com.java.dev.jim.personal.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "category_rule")
public class CategoryRule {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.EAGER) @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @Enumerated(EnumType.STRING)
    private BillSource source;
    private String keyword;
    @Column(nullable = false)
    private int priority;
    @Column(nullable = false)
    private boolean active;

    protected CategoryRule() { }
    public CategoryRule(Category category, BillSource source, String keyword, int priority, boolean active) { update(category, source, keyword, priority, active); }
    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public BillSource getSource() { return source; }
    public String getKeyword() { return keyword; }
    public int getPriority() { return priority; }
    public boolean isActive() { return active; }
    public void update(Category category, BillSource source, String keyword, int priority, boolean active) {
        this.category = category; this.source = source; this.keyword = keyword == null ? null : keyword.trim(); this.priority = priority; this.active = active;
    }
}
