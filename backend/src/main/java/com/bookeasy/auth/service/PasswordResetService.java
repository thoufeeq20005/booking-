package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.AuthProperties;
import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.entity.PasswordResetToken;
import com.bookeasy.auth.repository.PasswordResetTokenRepository;
import com.bookeasy.auth.service.SessionIssuer.AuthResult;
import com.bookeasy.auth.support.AuthEmails;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.auth.support.PasswordPolicy;
import com.bookeasy.auth.token.RefreshTokenService;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.notification.channel.DeliveryException;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.entity.UserStatus;
import com.bookeasy.user.repository.UserRepository;

@Service
public class PasswordResetService {

    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    private final PasswordResetTokenRepository repository;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final RefreshTokenService refreshTokenService;
    private final SessionIssuer sessionIssuer;
    private final AuthEmails authEmails;
    private final CryptoService crypto;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    private final Clock clock;

    public PasswordResetService(PasswordResetTokenRepository repository, UserRepository users,
                                PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                RefreshTokenService refreshTokenService, SessionIssuer sessionIssuer,
                                AuthEmails authEmails, CryptoService crypto, RateLimiter rateLimiter,
                                AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.refreshTokenService = refreshTokenService;
        this.sessionIssuer = sessionIssuer;
        this.authEmails = authEmails;
        this.crypto = crypto;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /** Always succeeds from the caller's view, so the endpoint can't be used to discover registered emails. */
    @Transactional
    public void requestReset(String rawEmail, RequestContext ctx) {
        String email = Identifiers.normalizeEmail(rawEmail);
        rateLimiter.hit("reset:ip:" + ctx.ip(), 10, Duration.ofHours(1));
        rateLimiter.hit("reset:email:" + email, 3, Duration.ofHours(1));
        User user = users.findByEmail(email).orElse(null);
        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            return;
        }
        String raw = crypto.randomToken(32);
        Instant now = clock.instant();
        repository.save(new PasswordResetToken(user.getId(), crypto.sha256Hex(raw),
                now.plus(properties.passwordResetTtl()), now));
        try {
            authEmails.sendPasswordReset(user, raw);
        } catch (DeliveryException e) {
            log.error("Password reset email for user {} not sent: {}", user.getId(), e.getMessage());
        }
    }

    /** Sets the new password and signs out every existing session. */
    @Transactional
    public void reset(AuthRequests.ResetPassword req) {
        Instant now = clock.instant();
        PasswordResetToken token = repository.findByTokenHash(crypto.sha256Hex(req.token()))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        if (token.isUsed()) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        if (token.isExpiredAt(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        passwordPolicy.validate("newPassword", req.newPassword());
        User user = users.findById(token.getUserId()).orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        SessionIssuer.requireActive(user);
        applyNewPassword(user, req.newPassword(), now);
        repository.invalidateAllForUser(user.getId(), now);
        // Proving control of the mailbox also proves the email address.
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
        }
    }

    /**
     * Signed-in password change (or first password for OTP/Google accounts). Other sessions are signed out;
     * the caller gets a fresh session.
     */
    @Transactional
    public AuthResult change(long userId, AuthRequests.ChangePassword req, RequestContext ctx) {
        rateLimiter.hit("change-password:user:" + userId, 5, Duration.ofMinutes(10));
        User user = users.findWithRolesById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (user.getPasswordHash() != null && (req.currentPassword() == null
                || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash()))) {
            throw ApiException.invalidField("currentPassword", "PASSWORD_INCORRECT", "Current password is incorrect");
        }
        passwordPolicy.validate("newPassword", req.newPassword());
        applyNewPassword(user, req.newPassword(), clock.instant());
        return sessionIssuer.complete(user, ctx);
    }

    private void applyNewPassword(User user, String password, Instant now) {
        user.setPasswordHash(passwordEncoder.encode(password));
        user.revokeAccessTokens(now);
        refreshTokenService.revokeAllForUser(user.getId());
    }
}
