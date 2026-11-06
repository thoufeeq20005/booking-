package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.dto.AuthResponses.OtpSent;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.entity.OtpPurpose;
import com.bookeasy.auth.otp.OtpService;
import com.bookeasy.auth.support.AuthEmails;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/**
 * Verifies an account's email address with a 6-digit code (5-minute expiry, 5 attempts, resend cooldown and
 * hourly cap, shared with the SMS codes through {@link OtpService}).
 */
@Service
public class EmailVerificationService {

    private final OtpService otpService;
    private final UserRepository users;
    private final AuthEmails authEmails;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public EmailVerificationService(OtpService otpService, UserRepository users, AuthEmails authEmails,
                                    RateLimiter rateLimiter, Clock clock) {
        this.otpService = otpService;
        this.users = users;
        this.authEmails = authEmails;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }


    /** A new code for the signed-in user's unverified email. */
    @Transactional
    public OtpSent resend(long userId) {
        rateLimiter.hit("verify-email:user:" + userId, 3, Duration.ofHours(1));
        User user = unverified(userId);
        long minutes = otpService.ttlMinutes();
        return otpService.send(user.getEmail(), OtpPurpose.VERIFY_EMAIL, new OtpService.CodeDelivery() {
            @Override
            public void deliver(String code) {
                authEmails.sendVerificationCode(user, code, minutes);
            }

            @Override
            public boolean visible() {
                return authEmails.codesVisibleOnScreen();
            }
        });
    }

    @Transactional(noRollbackFor = ApiException.class)
    public UserView verify(long userId, String code) {
        User user = unverified(userId);
        otpService.verify(user.getEmail(), OtpPurpose.VERIFY_EMAIL, code);
        Instant now = clock.instant();
        user.setEmailVerifiedAt(now);
        user.touch(now);
        return UserView.of(user);
    }

    private User unverified(long userId) {
        User user = users.findWithRolesById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (user.getEmail() == null || user.getEmailVerifiedAt() != null) {
            throw new ApiException(ErrorCode.CONFLICT, "Email is already verified");
        }
        return user;
    }
}
