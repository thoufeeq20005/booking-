package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.OtpSent;
import com.bookeasy.auth.entity.OtpPurpose;
import com.bookeasy.auth.otp.OtpService;
import com.bookeasy.auth.service.SessionIssuer.AuthResult;
import com.bookeasy.auth.support.AuthEmails;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.entity.UserStatus;
import com.bookeasy.user.repository.UserRepository;

/**
 * Passwordless sign-in for existing accounts: a 6-digit code is emailed to the account's address. Same limits as
 * the SMS code (5-minute expiry, 5 attempts, resend cooldown, hourly cap). Unknown addresses get the same response
 * as known ones, so the endpoint can't be used to discover who has an account.
 */
@Service
public class EmailCodeAuthService {

    private final OtpService otpService;
    private final UserRepository users;
    private final SessionIssuer sessionIssuer;
    private final AuthEmails authEmails;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public EmailCodeAuthService(OtpService otpService, UserRepository users, SessionIssuer sessionIssuer,
                                AuthEmails authEmails, RateLimiter rateLimiter, Clock clock) {
        this.otpService = otpService;
        this.users = users;
        this.sessionIssuer = sessionIssuer;
        this.authEmails = authEmails;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    @Transactional
    public OtpSent sendLoginCode(String rawEmail, RequestContext ctx) {
        String email = Identifiers.normalizeEmail(rawEmail);
        rateLimiter.hit("email-code-send:ip:" + ctx.ip(), 20, Duration.ofHours(1));
        User user = users.findByEmail(email).filter(u -> u.getStatus() == UserStatus.ACTIVE).orElse(null);
        if (user == null) {
            return otpService.notSent();
        }
        long minutes = otpService.ttlMinutes();
        return otpService.send(email, OtpPurpose.EMAIL_LOGIN, new OtpService.CodeDelivery() {
            @Override
            public void deliver(String code) {
                authEmails.sendLoginCode(user, code, minutes);
            }

            @Override
            public boolean visible() {
                return authEmails.codesVisibleOnScreen();
            }
        });
    }

    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(AuthRequests.EmailCodeLogin req, RequestContext ctx) {
        String email = Identifiers.normalizeEmail(req.email());
        rateLimiter.hit("email-code-verify:ip:" + ctx.ip(), 30, Duration.ofMinutes(10));
        User user = users.findByEmail(email).orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID));
        otpService.verify(email, OtpPurpose.EMAIL_LOGIN, req.code());
        if (user.getEmailVerifiedAt() == null) {
            // Receiving the code proves the person controls this inbox.
            user.setEmailVerifiedAt(clock.instant());
        }
        return sessionIssuer.afterFirstFactor(user, ctx);
    }
}
