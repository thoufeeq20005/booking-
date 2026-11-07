package com.bookeasy.auth.dto;

import java.time.Instant;
import java.util.List;

import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record UserView(
            long id,
            String fullName,
            String email,
            String phone,
            boolean emailVerified,
            boolean phoneVerified,
            List<RoleCode> roles,
            String preferredLanguage,
            boolean twoFactorEnabled,
            boolean hasPassword,
            String status,
            Instant createdAt) {

        public static UserView of(User u) {
            return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getEmailVerifiedAt() != null, u.getPhoneVerifiedAt() != null,
                    u.roleSet().stream().sorted().toList(), u.getPreferredLanguage(), u.isTwoFactorEnabled(),
                    u.getPasswordHash() != null, u.getStatus().name(), u.getCreatedAt());
        }
    }

    /** EMAIL_CODE_REQUIRED: the password was right; a code was emailed and must be entered to finish signing in. */
    public enum AuthStatus { AUTHENTICATED, TWO_FACTOR_REQUIRED, EMAIL_CODE_REQUIRED }

    /**
     * Either an access token + user, or a challenge to finish signing in. The refresh token travels only in its cookie.
     * emailCode is set for EMAIL_CODE_REQUIRED (when the code can be resent, and the code itself in development only).
     */
    public record Session(AuthStatus status, String accessToken, Instant accessTokenExpiresAt, String challengeToken,
                          UserView user, OtpSent emailCode) {

        public static Session authenticated(String accessToken, Instant expiresAt, UserView user) {
            return new Session(AuthStatus.AUTHENTICATED, accessToken, expiresAt, null, user, null);
        }

        public static Session challenge(String challengeToken) {
            return new Session(AuthStatus.TWO_FACTOR_REQUIRED, null, null, challengeToken, null, null);
        }

        public static Session emailCodeChallenge(String challengeToken, OtpSent sent) {
            return new Session(AuthStatus.EMAIL_CODE_REQUIRED, null, null, challengeToken, null, sent);
        }
    }

    /** Registration signs the user in immediately; verification can happen later. */
    public record Registered(Session session) {
    }

    /** demoCode is only present in development when no SMS provider is configured (see SmsSender#deliversToPhone). */
    public record OtpSent(long expiresInSeconds, long resendAfterSeconds, String demoCode) {
    }

    public record TwoFactorSetup(String secret, String otpauthUri) {
    }
}
