package com.bookeasy.category.entity;

import java.time.Instant;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String slug;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", nullable = false, columnDefinition = "json")
    private Map<String, String> name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "json")
    private Map<String, String> description;

    @Column(nullable = false, length = 48)
    private String icon;

    @Enumerated(EnumType.STRING)
    @Column(name = "booking_workflow", nullable = false, length = 16)
    private BookingWorkflow bookingWorkflow;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Category() {
    }

    public Category(String slug, Instant now) {
        this.slug = slug;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String slug, Map<String, String> name, Map<String, String> description, String icon,
                       BookingWorkflow workflow, boolean active, int sortOrder, Instant now) {
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.icon = icon;
        this.bookingWorkflow = workflow;
        this.active = active;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public Map<String, String> getName() {
        return name;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public String getIcon() {
        return icon;
    }

    public BookingWorkflow getBookingWorkflow() {
        return bookingWorkflow;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
