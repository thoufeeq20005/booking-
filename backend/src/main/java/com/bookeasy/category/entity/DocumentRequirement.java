package com.bookeasy.category.entity;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A document businesses in a category must (or may) upload for verification. */
@Entity
@Table(name = "category_document_requirements")
public class DocumentRequirement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false, updatable = false)
    private Long categoryId;

    @Column(nullable = false, length = 48)
    private String code;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "label_i18n", nullable = false, columnDefinition = "json")
    private Map<String, String> label;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "json")
    private Map<String, String> description;

    @Column(name = "is_mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "allowed_mime_types", nullable = false)
    private String allowedMimeTypes;

    @Column(name = "max_size_bytes", nullable = false)
    private int maxSizeBytes;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentRequirement() {
    }

    public DocumentRequirement(long categoryId, Instant now) {
        this.categoryId = categoryId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String code, Map<String, String> label, Map<String, String> description, boolean mandatory,
                       Set<String> allowedTypes, int maxSizeBytes, boolean active, int sortOrder, Instant now) {
        this.code = code;
        this.label = label;
        this.description = description;
        this.mandatory = mandatory;
        this.allowedMimeTypes = String.join(",", allowedTypes);
        this.maxSizeBytes = maxSizeBytes;
        this.active = active;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public Set<String> allowedTypes() {
        Set<String> out = new LinkedHashSet<>();
        Arrays.stream(allowedMimeTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(out::add);
        return out;
    }

    public Long getId() {
        return id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public String getCode() {
        return code;
    }

    public Map<String, String> getLabel() {
        return label;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public int getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
