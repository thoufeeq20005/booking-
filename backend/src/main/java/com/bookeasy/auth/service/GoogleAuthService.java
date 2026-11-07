package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.auth.entity.OAuthAccount;
import com.bookeasy.auth.google.GoogleIdTokenVerifier;
import com.bookeasy.auth.google.GoogleIdTokenVerifier.GoogleIdentity;
import com.bookeasy.auth.repository.OAuthAccountRepository;
import com.bookeasy.auth.service.SessionIssuer.AuthResult;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

@Service
public class GoogleAuthService {

    private final GoogleIdTokenVerifier verifier;
    private final OAuthAccountRepository oauthAccounts;
    private final UserRepository users;
    private final SessionIssuer sessionIssuer;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public GoogleAuthService(GoogleIdTokenVerifier verifier, OAuthAccountRepository oauthAccounts,
                             UserRepository users, SessionIssuer sessionIssuer, RateLimiter rateLimiter, Clock clock) {
        this.verifier = verifier;
        this.oauthAccounts = oauthAccounts;
        this.users = users;
        this.sessionIssuer = sessionIssuer;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    /**
     * Signs in a linked Google account, links Google to an existing account with the same verified email,
     * or creates a new customer. Linking requires Google to assert the email is verified, otherwise anyone
     * could claim an existing BookEasy account.
     */
    @Transactional
    public AuthResult login(String idToken, RequestContext ctx) {
        rateLimiter.hit("google:ip:" + ctx.ip(), 30, Duration.ofMinutes(10));
        GoogleIdentity identity = verifier.verify(idToken);
        Instant now = clock.instant();

        User user = oauthAccounts.findByProviderAndProviderSubject(OAuthAccount.GOOGLE, identity.subject())
                .flatMap(link -> users.findWithRolesById(link.getUserId()))
                .orElse(null);
        if (user == null) {
            if (!identity.emailVerified() || !StringUtils.hasText(identity.email())) {
                throw new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID, "Your Google email address is not verified");
            }
            String email = Identifiers.normalizeEmail(identity.email());
            user = users.findByEmail(email).orElse(null);
            if (user == null) {
                String name = StringUtils.hasText(identity.name()) ? identity.name() : email.substring(0, email.indexOf('@'));
                user = new User(name.length() > 120 ? name.substring(0, 120) : name, email, null, null, now);
                user.addRole(RoleCode.CUSTOMER);
                users.saveAndFlush(user);
            }
            if (user.getEmailVerifiedAt() == null) {
                user.setEmailVerifiedAt(now);
            }
            oauthAccounts.save(new OAuthAccount(user.getId(), OAuthAccount.GOOGLE, identity.subject(), email, now));
        }
        return sessionIssuer.afterFirstFactor(user, ctx);
    }
}
