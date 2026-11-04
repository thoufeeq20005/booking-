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
import com.bookeasy.auth.dto.AuthResponses.Registered;
import com.bookeasy.auth.service.SessionIssuer.AuthResult;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.auth.support.PasswordPolicy;
import com.bookeasy.auth.token.JwtTokenService;
import com.bookeasy.auth.token.JwtTokenService.TwoFactorChallenge;
import com.bookeasy.auth.token.RefreshTokenService;
import com.bookeasy.auth.twofactor.TwoFactorService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/** Email + password registration and sign-in, 2FA completion, refresh and logout. */
@Service
public class PasswordAuthService {

    private static final Logger log = LoggerFactory.getLogger(PasswordAuthService.class);
    private static final Duration WINDOW = Duration.ofMinutes(10);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SessionIssuer sessionIssuer;
    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final TwoFactorService twoFactorService;
    private final EmailVerificationService emailVerificationService;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    private final Clock clock;
    /** Compared against when the email is unknown, so response time doesn't reveal whether an account exists. */
    private final String dummyHash;

    public PasswordAuthService(UserRepository users, PasswordEncoder passwordEncoder, PasswordPolicy passwordPolicy,
                               SessionIssuer sessionIssuer, JwtTokenService jwtTokenService,
                               RefreshTokenService refreshTokenService, TwoFactorService twoFactorService,
                               EmailVerificationService emailVerificationService, RateLimiter rateLimiter,
                               AuthProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.sessionIssuer = sessionIssuer;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.twoFactorService = twoFactorService;
        this.emailVerificationService = emailVerificationService;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-0");
    }

    public record RegisterResult(Registered body, String refreshToken) {
    }

    @Transactional
    public RegisterResult register(AuthRequests.Register req, RequestContext ctx) {
        rateLimiter.hit("register:ip:" + ctx.ip(), 10, Duration.ofHours(1));
        String email = Identifiers.normalizeEmail(req.email());
        passwordPolicy.validate("password", req.password());
        if (users.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        Instant now = clock.instant();
        User user = new User(req.fullName().trim(), email, null, req.preferredLanguage(), now);
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.addRole(RoleCode.CUSTOMER);
        users.saveAndFlush(user);

        boolean sent = emailVerificationService.issueAndSend(user);
        AuthResult result = sessionIssuer.complete(user, ctx);
        return new RegisterResult(new Registered(result.session(), sent), result.refreshToken());
    }

    /** Failed attempts are committed even though the call fails; that is what makes lockout work. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(AuthRequests.Login req, RequestContext ctx) {
        String email = Identifiers.normalizeEmail(req.email());
        rateLimiter.hit("login:ip:" + ctx.ip(), 30, WINDOW);
        rateLimiter.hit("login:email:" + email, 10, WINDOW);

        Instant now = clock.instant();
        User user = users.findByEmail(email).orElse(null);
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(req.password(), dummyHash);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (user.isLockedAt(now)) {
            throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
        }
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            AuthProperties.Lockout lockout = properties.lockout();
            user.registerFailedLogin(lockout.maxFailures(), now.plus(lockout.duration()), now);
            if (user.isLockedAt(now)) {
                log.warn("Account {} locked after repeated failed logins", user.getId());
                throw new ApiException(ErrorCode.ACCOUNT_LOCKED);
            }
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        return sessionIssuer.afterFirstFactor(user, ctx);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult completeTwoFactor(AuthRequests.TwoFactorLogin req, RequestContext ctx) {
        TwoFactorChallenge challenge = jwtTokenService.parseTwoFactorChallenge(req.challengeToken());
        rateLimiter.hit("2fa:user:" + challenge.userId(), 5, WINDOW);
        User user = users.findWithRolesById(challenge.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        if (user.getTokenVersion() != challenge.tokenVersion() || !user.isTwoFactorEnabled()) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        twoFactorService.requireValidCode(user, req.code());
        return sessionIssuer.complete(user, ctx);
    }

    /** noRollbackFor: a detected token reuse revokes the family, and that must commit even though we fail. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult refresh(String rawRefreshToken, RequestContext ctx) {
        RefreshTokenService.Rotated rotated = refreshTokenService.rotate(rawRefreshToken, ctx.userAgent(), ctx.ip());
        User user = users.findWithRolesById(rotated.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        SessionIssuer.requireActive(user);
        return new AuthResult(sessionIssuer.accessSession(user), rotated.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeFamilyOf(rawRefreshToken);
    }

    /** Ends every session of the user on every device. */
    @Transactional
    public void logoutAll(long userId) {
        User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        user.revokeAccessTokens(clock.instant());
        refreshTokenService.revokeAllForUser(userId);
    }
}
