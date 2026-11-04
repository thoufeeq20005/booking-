package com.bookeasy.auth.service;

import java.time.Clock;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.dto.AuthResponses.Session;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.token.JwtTokenService;
import com.bookeasy.auth.token.RefreshTokenService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.entity.UserStatus;

/**
 * Final step of every sign-in method: status checks, optional 2FA challenge, then tokens.
 * Joins the caller's transaction; a rejection must not mark it rollback-only, because callers commit
 * failed-attempt bookkeeping (noRollbackFor) even when sign-in fails.
 */
@Component
public class SessionIssuer {

    private final JwtTokenService jwtTokenService;
    private final RefreshTokenService refreshTokenService;
    private final Clock clock;

    public SessionIssuer(JwtTokenService jwtTokenService, RefreshTokenService refreshTokenService, Clock clock) {
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenService = refreshTokenService;
        this.clock = clock;
    }

    /** Result for controllers: the response body plus the refresh token to place in the cookie (if any). */
    public record AuthResult(Session session, String refreshToken) {
    }

    public static void requireActive(User user) {
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ApiException(ErrorCode.ACCOUNT_SUSPENDED);
        }
    }

    /** First factor passed: either challenge for TOTP or finish the sign-in. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public AuthResult afterFirstFactor(User user, RequestContext ctx) {
        requireActive(user);
        if (user.isTwoFactorEnabled()) {
            return new AuthResult(Session.challenge(jwtTokenService.issueTwoFactorChallenge(user).value()), null);
        }
        return complete(user, ctx);
    }

    /** All factors passed. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ApiException.class)
    public AuthResult complete(User user, RequestContext ctx) {
        requireActive(user);
        user.registerSuccessfulLogin(clock.instant());
        RefreshTokenService.Issued refresh = refreshTokenService.issueNewFamily(user.getId(), ctx.userAgent(), ctx.ip());
        return new AuthResult(accessSession(user), refresh.rawToken());
    }

    public Session accessSession(User user) {
        JwtTokenService.IssuedToken access = jwtTokenService.issueAccessToken(user);
        return Session.authenticated(access.value(), access.expiresAt(), UserView.of(user));
    }
}
