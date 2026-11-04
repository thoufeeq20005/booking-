package com.bookeasy.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.audit.AuditService;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.support.AuthEmails;
import com.bookeasy.auth.token.RefreshTokenService;
import com.bookeasy.common.api.PageResponse;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.security.AuthenticatedUser;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.entity.UserStatus;
import com.bookeasy.user.repository.UserRepository;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final AuthEmails emails;
    private final Clock clock;

    public AdminUserService(UserRepository users, RefreshTokenService refreshTokenService, AuditService auditService,
                            AuthEmails emails, Clock clock) {
        this.users = users;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.emails = emails;
        this.clock = clock;
    }

    /**
     * Turns off two-step verification for a user who lost their authenticator (after support has confirmed
     * their identity). Signs them out everywhere, audits the reason and emails a security notice.
     */
    @Transactional
    public UserView resetTwoFactor(AuthenticatedUser admin, long userId, String reason, String ip) {
        String why = reason == null ? "" : reason.strip();
        if (why.length() < 5) {
            throw ApiException.invalidField("reason", "NotBlank", "Record how the user's identity was confirmed");
        }
        if (admin.id() == userId) {
            throw new ApiException(ErrorCode.CONFLICT, "Use your own account security page");
        }
        User user = users.findWithRolesById(userId).orElseThrow(() -> ApiException.notFound("User"));
        if (!user.isTwoFactorEnabled()) {
            throw new ApiException(ErrorCode.TWO_FACTOR_NOT_ENABLED);
        }
        Instant now = clock.instant();
        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecretEnc(null);
        user.setTwoFactorLastStep(null);
        user.revokeAccessTokens(now);
        refreshTokenService.revokeAllForUser(userId);
        auditService.record(admin.id(), admin.primaryRole(), "USER_2FA_RESET", "USER", String.valueOf(userId),
                Map.of("reason", why), ip);
        try {
            emails.sendTwoFactorReset(user);
        } catch (RuntimeException e) {
            // The reset stands even if the notice can't be delivered; the audit log records it.
            log.warn("Could not send the two-step reset notice to user {}", userId, e);
        }
        return UserView.of(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<UserView> search(String q, UserStatus status, RoleCode role, int page, int size) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(users.search(query, status, role, pageable), UserView::of);
    }

    /** Suspending signs the user out everywhere immediately. Admins cannot change their own status. */
    @Transactional
    public UserView changeStatus(AuthenticatedUser admin, long userId, UserStatus newStatus, String reason, String ip) {
        if (newStatus == UserStatus.DELETED) {
            throw ApiException.invalidField("status", "UNSUPPORTED", "Use the account deletion workflow");
        }
        if (admin.id() == userId) {
            throw new ApiException(ErrorCode.CONFLICT, "You cannot change your own status");
        }
        User user = users.findWithRolesById(userId).orElseThrow(() -> ApiException.notFound("User"));
        UserStatus previous = user.getStatus();
        if (previous == newStatus) {
            return UserView.of(user);
        }
        Instant now = clock.instant();
        user.setStatus(newStatus);
        if (newStatus == UserStatus.SUSPENDED) {
            user.revokeAccessTokens(now);
            refreshTokenService.revokeAllForUser(userId);
        } else {
            user.touch(now);
        }
        auditService.record(admin.id(), admin.primaryRole(),
                newStatus == UserStatus.SUSPENDED ? "USER_SUSPENDED" : "USER_REACTIVATED", "USER",
                String.valueOf(userId), Map.of("from", previous.name(), "to", newStatus.name(),
                        "reason", reason == null ? "" : reason), ip);
        return UserView.of(user);
    }
}
