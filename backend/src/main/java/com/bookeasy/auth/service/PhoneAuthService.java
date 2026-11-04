package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.OtpSent;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.entity.OtpPurpose;
import com.bookeasy.auth.otp.OtpService;
import com.bookeasy.auth.service.SessionIssuer.AuthResult;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/** Mobile number + SMS OTP: passwordless sign-in/sign-up, and verifying a number on an existing account. */
@Service
public class PhoneAuthService {

    private final OtpService otpService;
    private final UserRepository users;
    private final SessionIssuer sessionIssuer;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PhoneAuthService(OtpService otpService, UserRepository users, SessionIssuer sessionIssuer,
                            RateLimiter rateLimiter, Clock clock) {
        this.otpService = otpService;
        this.users = users;
        this.sessionIssuer = sessionIssuer;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public OtpSent sendLoginCode(String rawPhone, RequestContext ctx) {
        String phone = phone(rawPhone);
        rateLimiter.hit("otp-send:ip:" + ctx.ip(), 20, Duration.ofHours(1));
        return otpService.send(phone, OtpPurpose.LOGIN);
    }

    /**
     * Signs in the owner of the number, or creates a customer account for a new number. A new number needs a
     * name; that check runs before the code is consumed so the user can retry with the same code.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResult login(AuthRequests.OtpLogin req, RequestContext ctx) {
        String phone = phone(req.phone());
        rateLimiter.hit("otp-verify:ip:" + ctx.ip(), 30, Duration.ofMinutes(10));
        User user = users.findByPhone(phone).orElse(null);
        if (user == null && !StringUtils.hasText(req.fullName())) {
            throw new ApiException(ErrorCode.FULL_NAME_REQUIRED);
        }
        otpService.verify(phone, OtpPurpose.LOGIN, req.code());
        Instant now = clock.instant();
        if (user == null) {
            user = new User(req.fullName().trim(), null, phone, req.preferredLanguage(), now);
            user.addRole(RoleCode.CUSTOMER);
            user.setPhoneVerifiedAt(now);
            users.saveAndFlush(user);
        } else if (user.getPhoneVerifiedAt() == null) {
            user.setPhoneVerifiedAt(now);
        }
        return sessionIssuer.afterFirstFactor(user, ctx);
    }

    public OtpSent sendVerificationCode(long userId, String rawPhone) {
        String phone = phone(rawPhone);
        users.findByPhone(phone).filter(u -> u.getId() != userId).ifPresent(u -> {
            throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED);
        });
        rateLimiter.hit("otp-send:user:" + userId, 10, Duration.ofHours(1));
        return otpService.send(phone, OtpPurpose.VERIFY_PHONE);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public UserView verifyPhone(long userId, AuthRequests.PhoneVerify req) {
        String phone = phone(req.phone());
        User user = users.findWithRolesById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (users.findByPhone(phone).filter(u -> u.getId() != userId).isPresent()) {
            throw new ApiException(ErrorCode.PHONE_ALREADY_REGISTERED);
        }
        otpService.verify(phone, OtpPurpose.VERIFY_PHONE, req.code());
        Instant now = clock.instant();
        user.setPhone(phone);
        user.setPhoneVerifiedAt(now);
        user.touch(now);
        return UserView.of(user);
    }

    private static String phone(String raw) {
        return Identifiers.normalizeIndianMobile(raw).orElseThrow(() ->
                ApiException.invalidField("phone", "PHONE_INVALID", "Enter a valid 10-digit Indian mobile number"));
    }
}
