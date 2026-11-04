package com.bookeasy.auth.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.AuthProperties;
import com.bookeasy.auth.entity.EmailVerification;
import com.bookeasy.auth.repository.EmailVerificationRepository;
import com.bookeasy.auth.support.AuthEmails;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.common.ratelimit.RateLimiter;
import com.bookeasy.notification.channel.DeliveryException;
import com.bookeasy.user.entity.User;
import com.bookeasy.user.repository.UserRepository;

@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final EmailVerificationRepository repository;
    private final UserRepository users;
    private final AuthEmails authEmails;
    private final CryptoService crypto;
    private final RateLimiter rateLimiter;
    private final AuthProperties properties;
    private final Clock clock;

    public EmailVerificationService(EmailVerificationRepository repository, UserRepository users, AuthEmails authEmails,
                                    CryptoService crypto, RateLimiter rateLimiter, AuthProperties properties,
                                    Clock clock) {
        this.repository = repository;
        this.users = users;
        this.authEmails = authEmails;
        this.crypto = crypto;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.clock = clock;
    }

    /** Creates a token and emails it. Returns false (and keeps the account) if the email couldn't be sent. */
    @Transactional
    public boolean issueAndSend(User user) {
        String raw = crypto.randomToken(32);
        Instant now = clock.instant();
        repository.save(new EmailVerification(user.getId(), user.getEmail(), crypto.sha256Hex(raw),
                now.plus(properties.emailVerificationTtl()), now));
        try {
            authEmails.sendVerification(user, raw);
            return true;
        } catch (DeliveryException e) {
            log.error("Verification email for user {} not sent: {}", user.getId(), e.getMessage());
            return false;
        }
    }

    @Transactional
    public boolean resend(long userId) {
        rateLimiter.hit("verify-email:user:" + userId, 3, Duration.ofHours(1));
        User user = users.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (user.getEmail() == null || user.getEmailVerifiedAt() != null) {
            throw new ApiException(ErrorCode.CONFLICT, "Email is already verified");
        }
        if (!issueAndSend(user)) {
            throw new ApiException(ErrorCode.PROVIDER_UNAVAILABLE, "We couldn't send the email. Please try again later");
        }
        return true;
    }

    @Transactional
    public void verify(String rawToken) {
        Instant now = clock.instant();
        EmailVerification token = repository.findByTokenHash(crypto.sha256Hex(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        if (token.isUsed()) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        if (token.isExpiredAt(now)) {
            throw new ApiException(ErrorCode.TOKEN_EXPIRED);
        }
        User user = users.findById(token.getUserId()).orElseThrow(() -> new ApiException(ErrorCode.INVALID_TOKEN));
        if (!token.getEmail().equals(user.getEmail())) {
            throw new ApiException(ErrorCode.INVALID_TOKEN);
        }
        token.markUsed(now);
        if (user.getEmailVerifiedAt() == null) {
            user.setEmailVerifiedAt(now);
            user.touch(now);
        }
    }
}
