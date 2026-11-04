package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.audit.AuditService;
import com.bookeasy.auth.AuthProperties;
import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.auth.support.PasswordPolicy;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/**
 * Creates the first Super Admin. Works only when BOOTSTRAP_ADMIN_EMAIL and BOOTSTRAP_ADMIN_TOKEN are set,
 * the request matches both, and no Super Admin exists yet. There is no default password anywhere.
 */
@Service
public class AdminBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapService.class);
    private static final int MIN_TOKEN_LENGTH = 32;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final CryptoService crypto;
    private final RateLimiter rateLimiter;
    private final AuditService auditService;
    private final AuthProperties.Bootstrap config;
    private final Clock clock;

    public AdminBootstrapService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                                 CryptoService crypto, RateLimiter rateLimiter, AuditService auditService,
                                 AuthProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.crypto = crypto;
        this.rateLimiter = rateLimiter;
        this.auditService = auditService;
        this.config = properties.bootstrap();
        this.clock = clock;
    }

    public boolean isEnabled() {
        return StringUtils.hasText(config.email()) && config.token() != null
                && config.token().length() >= MIN_TOKEN_LENGTH;
    }

    @Transactional
    public UserView createFirstSuperAdmin(AuthRequests.BootstrapAdmin req, RequestContext ctx) {
        rateLimiter.hit("bootstrap:ip:" + ctx.ip(), 5, Duration.ofHours(1));
        if (!isEnabled()) {
            throw new ApiException(ErrorCode.BOOTSTRAP_UNAVAILABLE);
        }
        String email = Identifiers.normalizeEmail(req.email());
        if (!crypto.constantTimeEquals(config.token(), req.token())
                || !email.equals(Identifiers.normalizeEmail(config.email()))) {
            log.warn("Rejected Super Admin bootstrap attempt from {}", ctx.ip());
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (users.existsWithRole(RoleCode.SUPER_ADMIN)) {
            throw new ApiException(ErrorCode.BOOTSTRAP_ALREADY_DONE);
        }
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        passwordPolicy.validate("password", req.password());
        Instant now = clock.instant();
        User admin = new User(req.fullName().trim(), email, null, null, now);
        admin.setPasswordHash(passwordEncoder.encode(req.password()));
        admin.setEmailVerifiedAt(now);
        admin.addRole(RoleCode.SUPER_ADMIN);
        users.saveAndFlush(admin);
        auditService.record(admin.getId(), RoleCode.SUPER_ADMIN.name(), "SUPER_ADMIN_BOOTSTRAPPED", "USER",
                String.valueOf(admin.getId()), Map.of("email", email), ctx.ip());
        log.info("First Super Admin created (user {}). Remove BOOTSTRAP_ADMIN_TOKEN now.", admin.getId());
        return UserView.of(admin);
    }
}
