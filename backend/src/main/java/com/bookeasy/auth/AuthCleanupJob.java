package com.bookeasy.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.repository.EmailVerificationRepository;
import com.bookeasy.auth.repository.OtpVerificationRepository;
import com.bookeasy.auth.repository.PasswordResetTokenRepository;
import com.bookeasy.auth.repository.RefreshTokenRepository;
import com.bookeasy.common.ratelimit.RateLimiter;

/** Deletes expired auth artefacts. Safe to run on several instances at once (plain range deletes). */
@Component
public class AuthCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);
    /** Keep expired rows a little longer to help investigate abuse. */
    private static final Duration GRACE = Duration.ofDays(7);

    private final RefreshTokenRepository refreshTokens;
    private final EmailVerificationRepository emailVerifications;
    private final PasswordResetTokenRepository passwordResets;
    private final OtpVerificationRepository otps;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public AuthCleanupJob(RefreshTokenRepository refreshTokens, EmailVerificationRepository emailVerifications,
                          PasswordResetTokenRepository passwordResets, OtpVerificationRepository otps,
                          RateLimiter rateLimiter, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.emailVerifications = emailVerifications;
        this.passwordResets = passwordResets;
        this.otps = otps;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Scheduled(cron = "${bookeasy.auth.cleanup-cron:0 30 3 * * *}", zone = "Asia/Kolkata")
    @Transactional
    public void purgeExpired() {
        Instant cutoff = clock.instant().minus(GRACE);
        int removed = refreshTokens.deleteExpiredBefore(cutoff)
                + emailVerifications.deleteExpiredBefore(cutoff)
                + passwordResets.deleteExpiredBefore(cutoff)
                + otps.deleteExpiredBefore(cutoff)
                + rateLimiter.purgeOlderThan(clock.instant().minus(Duration.ofDays(1)));
        log.info("Auth cleanup removed {} expired rows", removed);
    }
}
