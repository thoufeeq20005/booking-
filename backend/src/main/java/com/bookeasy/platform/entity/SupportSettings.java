package com.bookeasy.platform.entity;

import java.time.Instant;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Single-row (id = 1) customer support configuration managed by Super Admin. */
@Entity
@Table(name = "support_settings")
public class SupportSettings {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(columnDefinition = "TINYINT")
    private Short id;

    @Column(name = "support_email")
    private String supportEmail;

    @Column(name = "support_phone", length = 20)
    private String supportPhone;

    @Column(name = "working_days", nullable = false, length = 64)
    private String workingDays;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "is_available", nullable = false)
    private boolean available;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    protected SupportSettings() {
    }

    public Short getId() {
        return id;
    }

    public String getSupportEmail() {
        return supportEmail;
    }

    public String getSupportPhone() {
        return supportPhone;
    }

    public String getWorkingDays() {
        return workingDays;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isAvailable() {
        return available;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }
}
