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

    public enum AuthStatus { AUTHENTICATED, TWO_FACTOR_REQUIRED }

    /** Either an access token + user, or a 2FA challenge. The refresh token travels only in its cookie. */
    public record Session(AuthStatus status, String accessToken, Instant accessTokenExpiresAt, String challengeToken,
                          UserView user) {

        public static Session authenticated(String accessToken, Instant expiresAt, UserView user) {
            return new Session(AuthStatus.AUTHENTICATED, accessToken, expiresAt, null, user);
        }

        public static Session challenge(String challengeToken) {
            return new Session(AuthStatus.TWO_FACTOR_REQUIRED, null, null, challengeToken, null);
        }
    }

    /** Registration signs the user in immediately; verification can happen later. */
    public record Registered(Session session, boolean verificationEmailSent) {
    }

    public record OtpSent(long expiresInSeconds, long resendAfterSeconds) {
    }

    public record TwoFactorSetup(String secret, String otpauthUri) {
    }
}
