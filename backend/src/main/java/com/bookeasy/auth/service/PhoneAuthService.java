package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.dto.AuthRequests;
import com.bookeasy.auth.dto.AuthResponses.OtpSent;
import com.bookeasy.auth.dto.AuthResponses.UserView;
import com.bookeasy.auth.entity.OtpPurpose;
import com.bookeasy.auth.otp.OtpService;
import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

/** Adding and verifying a mobile number on a signed-in account with an SMS code. */
@Service
public class PhoneAuthService {

    private final OtpService otpService;
    private final UserRepository users;
    private final RateLimiter rateLimiter;
    private final Clock clock;

    public PhoneAuthService(OtpService otpService, UserRepository users, RateLimiter rateLimiter, Clock clock) {
        this.otpService = otpService;
        this.users = users;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
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
