package com.bookeasy.staff;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One weekly working window of a staff member at a branch, in the branch's local time. */
@Entity
@Table(name = "staff_availability")
public class StaffAvailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_id", nullable = false, updatable = false)
    private Long staffId;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "day_of_week", nullable = false, columnDefinition = "TINYINT")
    private int dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected StaffAvailability() {
    }

    public StaffAvailability(long staffId, long branchId, int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.staffId = staffId;
        this.branchId = branchId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public Long getStaffId() { return staffId; }
    public Long getBranchId() { return branchId; }
    public int getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
