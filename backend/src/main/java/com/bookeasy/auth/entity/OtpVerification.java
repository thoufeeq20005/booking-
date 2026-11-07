package com.bookeasy.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "otp_verifications")
public class OtpVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalised mobile number (E.164) or email address, depending on the purpose. */
    @Column(name = "target", nullable = false, length = 255)
    private String target;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private OtpPurpose purpose;

    @Column(name = "code_hash", nullable = false, columnDefinition = "CHAR(64)")
    private String codeHash;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected OtpVerification() {
    }

    public OtpVerification(String target, OtpPurpose purpose, String codeHash, Instant expiresAt, Instant createdAt) {
        this.target = target;
        this.purpose = purpose;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void registerAttempt() {
        attempts++;
    }

    public void consume(Instant now) {
        consumedAt = now;
    }

    public boolean isUsable(Instant now, int maxAttempts) {
        return consumedAt == null && expiresAt.isAfter(now) && attempts < maxAttempts;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
