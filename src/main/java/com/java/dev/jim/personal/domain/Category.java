package com.java.dev.jim.personal.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "category")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    @Column(nullable = false)
    private boolean active;
    @Column(nullable = false)
    private int sortOrder;

    protected Category() { }
    public Category(String name, boolean active, int sortOrder) { this.name = name; this.active = active; this.sortOrder = sortOrder; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public void update(String name, boolean active, int sortOrder) { this.name = name; this.active = active; this.sortOrder = sortOrder; }
}
