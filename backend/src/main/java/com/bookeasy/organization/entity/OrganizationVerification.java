package com.bookeasy.organization.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One entry in an organization's verification history (append-only). */
@Entity
@Table(name = "organization_verifications")
public class OrganizationVerification {

    public enum Action { SUBMITTED, APPROVED, REJECTED, SUSPENDED, REINSTATED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private Long organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false, length = 16, updatable = false)
    private OrganizationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 16, updatable = false)
    private OrganizationStatus toStatus;

    @Column(length = 1000, updatable = false)
    private String reason;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OrganizationVerification() {
    }

    public OrganizationVerification(long organizationId, Action action, OrganizationStatus from,
                                    OrganizationStatus to, String reason, long actorUserId, Instant now) {
        this.organizationId = organizationId;
        this.action = action;
        this.fromStatus = from;
        this.toStatus = to;
        this.reason = reason;
        this.actorUserId = actorUserId;
        this.createdAt = now;
    }

    public Action getAction() {
        return action;
    }

    public OrganizationStatus getFromStatus() {
        return fromStatus;
    }

    public OrganizationStatus getToStatus() {
        return toStatus;
    }

    public String getReason() {
        return reason;
    }

    public Long getActorUserId() {
        return actorUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
