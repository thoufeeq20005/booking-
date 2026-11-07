package com.bookeasy.auth.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "email_verifications")
public class EmailVerification extends OneTimeToken {

    /** The address being verified; a later email change makes this token useless. */
    @Column(nullable = false)
    private String email;

    protected EmailVerification() {
    }

    public EmailVerification(Long userId, String email, String tokenHash, Instant expiresAt, Instant createdAt) {
        super(userId, tokenHash, expiresAt, createdAt);
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}
