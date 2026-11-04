package com.bookeasy.staff;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Time a staff member is away, as UTC instants [startsAt, endsAt). */
@Entity
@Table(name = "staff_leaves")
public class StaffLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private Long staffId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StaffLeave() {
    }

    public StaffLeave(long staffId, Instant startsAt, Instant endsAt, String reason, long createdBy, Instant now) {
        this.staffId = staffId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.reason = reason;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public Long getStaffId() { return staffId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public String getReason() { return reason; }
}
