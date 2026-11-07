package com.bookeasy.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bookeasy.auth")
public record AuthProperties(
        String jwtSecret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        Duration twoFactorChallengeTtl,
        Duration emailVerificationTtl,
        Duration passwordResetTtl,
        RefreshCookie refreshCookie,
        Otp otp,
        Lockout lockout,
        Google google,
        Bootstrap bootstrap,
        boolean passwordLoginEmailCode) {

    /**
     * @param secure   send only over HTTPS (must be true in production)
     * @param sameSite Strict when the SPA and API share a site (dev proxy / Vercel rewrite)
     */
    public record RefreshCookie(String name, String path, boolean secure, String sameSite) {
    }

    public record Otp(int length, Duration ttl, int maxAttempts, Duration resendCooldown, int maxSendsPerHour) {
    }

    public record Lockout(int maxFailures, Duration duration) {
    }

    public record Google(String clientId) {
    }

    /** One-time creation of the first Super Admin; both values must be set, and only work while none exists. */
    public record Bootstrap(String email, String token) {
    }
}
