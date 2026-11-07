package com.bookeasy.platform.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "platform_settings")
public class PlatformSetting {

    public enum ValueType { STRING, INTEGER, DECIMAL, BOOLEAN, LIST }

    @Id
    @Column(name = "setting_key", length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 1000)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private ValueType valueType;

    @Column(nullable = false)
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean publicSetting;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    protected PlatformSetting() {
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public ValueType getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }

    public boolean isPublicSetting() {
        return publicSetting;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
