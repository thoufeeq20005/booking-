package com.bookeasy.organization.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A whole day the branch is closed, as a calendar date in the branch's timezone. */
@Entity
@Table(name = "holidays")
public class Holiday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Holiday() {
    }

    public Holiday(long branchId, LocalDate date, String reason, Instant now) {
        this.branchId = branchId;
        this.date = date;
        this.reason = reason;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getBranchId() {
        return branchId;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getReason() {
        return reason;
    }
}
