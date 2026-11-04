package com.bookeasy.staff;

import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A person working for an organization. {@code userId} stays null until an invitation is accepted. */
@Entity
@Table(name = "staff_profiles")
public class StaffProfile {

    public enum Status { INVITED, ACTIVE, INACTIVE }

    /** What a staff member may do beyond seeing their own schedule. */
    public enum Permission { CAN_CONFIRM, CAN_CANCEL, CAN_EDIT_HOURS }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(length = 40)
    private String title;

    @Column(length = 150)
    private String specialisation;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private Set<Permission> permissions = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.INVITED;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_branches", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "branch_id")
    private Set<Long> branchIds = new HashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "staff_services", joinColumns = @JoinColumn(name = "staff_id"))
    @Column(name = "service_id")
    private Set<Long> serviceIds = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StaffProfile() {
    }

    public StaffProfile(long organizationId, Instant now) {
        this.organizationId = organizationId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String displayName, String title, String specialisation, Set<Permission> permissions,
                       Set<Long> branchIds, Set<Long> serviceIds, Instant now) {
        this.displayName = displayName;
        this.title = title;
        this.specialisation = specialisation;
        this.permissions = permissions.isEmpty() ? new HashSet<>() : new HashSet<>(EnumSet.copyOf(permissions));
        this.branchIds.clear();
        this.branchIds.addAll(branchIds);
        this.serviceIds.clear();
        this.serviceIds.addAll(serviceIds);
        this.updatedAt = now;
    }

    public void link(long userId, Instant now) {
        this.userId = userId;
        this.status = Status.ACTIVE;
        this.updatedAt = now;
    }

    public void setStatus(Status status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public boolean can(Permission p) {
        return permissions.contains(p);
    }

    public Long getId() { return id; }
    public Long getOrganizationId() { return organizationId; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getTitle() { return title; }
    public String getSpecialisation() { return specialisation; }
    public Set<Permission> getPermissions() { return permissions; }
    public Status getStatus() { return status; }
    public Set<Long> getBranchIds() { return branchIds; }
    public Set<Long> getServiceIds() { return serviceIds; }
}
