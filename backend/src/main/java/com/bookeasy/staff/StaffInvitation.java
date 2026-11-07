package com.bookeasy.staff;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "staff_invitations")
public class StaffInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "staff_profile_id", nullable = false, updatable = false)
    private Long staffProfileId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "token_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StaffInvitation() {
    }

    public StaffInvitation(long staffProfileId, String email, String tokenHash, Instant expiresAt, long invitedBy,
                           Instant now) {
        this.staffProfileId = staffProfileId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.invitedBy = invitedBy;
        this.createdAt = now;
    }

    public boolean isOpenAt(Instant now) {
        return acceptedAt == null && revokedAt == null && expiresAt.isAfter(now);
    }

    public void accept(Instant now) { this.acceptedAt = now; }
    public void revoke(Instant now) { this.revokedAt = now; }

    public Long getId() { return id; }
    public Long getStaffProfileId() { return staffProfileId; }
    public String getEmail() { return email; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getAcceptedAt() { return acceptedAt; }
    public Instant getRevokedAt() { return revokedAt; }
}
