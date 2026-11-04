package com.bookeasy.auth.otp;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.AuthProperties;
import com.bookeasy.auth.dto.AuthResponses.OtpSent;
import com.bookeasy.auth.entity.OtpPurpose;
import com.bookeasy.auth.entity.OtpVerification;
import com.bookeasy.auth.repository.OtpVerificationRepository;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.notification.channel.DeliveryException;
import com.bookeasy.notification.channel.SmsSender;

/**
 * One-time codes sent by SMS. Codes are HMAC-hashed, expire, allow a limited number of attempts and
 * enforce a resend cooldown plus an hourly cap per number.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpVerificationRepository repository;
    private final SmsSender smsSender;
    private final CryptoService crypto;
    private final AuthProperties.Otp config;
    private final Clock clock;

    public OtpService(OtpVerificationRepository repository, SmsSender smsSender, CryptoService crypto,
                      AuthProperties properties, Clock clock) {
        this.repository = repository;
        this.smsSender = smsSender;
        this.crypto = crypto;
        this.config = properties.otp();
        this.clock = clock;
    }

    @Transactional
    public OtpSent send(String phone, OtpPurpose purpose) {
        Instant now = clock.instant();
        repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose).ifPresent(last -> {
            if (last.getCreatedAt().plus(config.resendCooldown()).isAfter(now)) {
                throw new ApiException(ErrorCode.OTP_RESEND_TOO_SOON);
            }
        });
        if (repository.countByPhoneAndPurposeAndCreatedAtAfter(phone, purpose, now.minus(Duration.ofHours(1)))
                >= config.maxSendsPerHour()) {
            throw new ApiException(ErrorCode.RATE_LIMITED);
        }
        String code = crypto.randomDigits(config.length());
        repository.save(new OtpVerification(phone, purpose, hash(phone, purpose, code), now.plus(config.ttl()), now));
        try {
            smsSender.sendOtp(phone, code);
        } catch (DeliveryException e) {
            // Roll back the stored code so the user isn't blocked by a cooldown for a message never sent.
            log.error("OTP delivery failed: {}", e.getMessage());
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "We couldn't send the code. Please try again later");
        }
        return new OtpSent(config.ttl().toSeconds(), config.resendCooldown().toSeconds());
    }

    /**
     * Checks the latest code for the number. Failed attempts are committed even though the call fails,
     * so attempt limits cannot be bypassed.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public void verify(String phone, OtpPurpose purpose, String code) {
        Instant now = clock.instant();
        OtpVerification otp = repository.findFirstByPhoneAndPurposeOrderByCreatedAtDesc(phone, purpose)
                .orElseThrow(() -> new ApiException(ErrorCode.OTP_INVALID));
        if (otp.getConsumedAt() != null) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        }
        if (!otp.getExpiresAt().isAfter(now) || otp.getAttempts() >= config.maxAttempts()) {
            throw new ApiException(ErrorCode.OTP_EXPIRED);
        }
        otp.registerAttempt();
        if (!crypto.constantTimeEquals(otp.getCodeHash(), hash(phone, purpose, code))) {
            throw new ApiException(ErrorCode.OTP_INVALID);
        }
        otp.consume(now);
    }

    private String hash(String phone, OtpPurpose purpose, String code) {
        return crypto.hmacHex(purpose + ":" + phone + ":" + code);
    }
}
