package com.bookeasy.organization.entity;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Opening hours for one ISO day of week (1 = Monday … 7 = Sunday), in the branch's timezone. */
@Entity
@Table(name = "working_hours")
public class WorkingHours {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private Long branchId;

    @Column(name = "day_of_week", nullable = false, columnDefinition = "TINYINT")
    private int dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    @Column(name = "break_start")
    private LocalTime breakStart;

    @Column(name = "break_end")
    private LocalTime breakEnd;

    protected WorkingHours() {
    }

    public WorkingHours(long branchId, int dayOfWeek, LocalTime opensAt, LocalTime closesAt, LocalTime breakStart,
                        LocalTime breakEnd) {
        this.branchId = branchId;
        this.dayOfWeek = dayOfWeek;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
        this.breakStart = breakStart;
        this.breakEnd = breakEnd;
    }

    public Long getBranchId() {
        return branchId;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }

    public LocalTime getBreakStart() {
        return breakStart;
    }

    public LocalTime getBreakEnd() {
        return breakEnd;
    }
}
