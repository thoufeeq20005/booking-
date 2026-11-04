package com.bookeasy.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.audit.AuditService;
import com.bookeasy.auth.dto.AuthResponses.UserView;
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

    private final UserRepository users;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final Clock clock;

    public AdminUserService(UserRepository users, RefreshTokenService refreshTokenService, AuditService auditService,
                            Clock clock) {
        this.users = users;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.clock = clock;
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
