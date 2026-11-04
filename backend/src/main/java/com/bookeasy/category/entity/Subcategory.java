package com.bookeasy.category.entity;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subcategories")
public class Subcategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Column(nullable = false, length = 64)
    private String slug;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false, columnDefinition = "json")
    private Map<String, String> name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Subcategory() {
    }

    public Subcategory(long categoryId, Instant now) {
        this.categoryId = categoryId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String slug, Map<String, String> name, boolean active, int sortOrder, Instant now) {
        this.slug = slug;
        this.name = name;
        this.active = active;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getSlug() {
        return slug;
    }

    public Map<String, String> getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
