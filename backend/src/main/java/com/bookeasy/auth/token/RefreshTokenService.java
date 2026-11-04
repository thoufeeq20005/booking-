package com.bookeasy.auth.token;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bookeasy.auth.AuthProperties;
import com.bookeasy.auth.entity.RefreshToken;
import com.bookeasy.auth.repository.RefreshTokenRepository;
import com.bookeasy.common.crypto.CryptoService;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

/**
 * Opaque, rotating refresh tokens. Each refresh revokes the presented token and issues a successor in
 * the same family. Presenting an already-rotated token means it was copied, so the whole family is revoked.
 */
@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    static final Duration REUSE_GRACE = Duration.ofSeconds(10);

    private final RefreshTokenRepository repository;
    private final CryptoService crypto;
    private final AuthProperties properties;
    private final Clock clock;

    public RefreshTokenService(RefreshTokenRepository repository, CryptoService crypto, AuthProperties properties,
                               Clock clock) {
        this.repository = repository;
        this.crypto = crypto;
        this.properties = properties;
        this.clock = clock;
    }

    public record Issued(String rawToken, RefreshToken entity) {
    }

    public record Rotated(long userId, String rawToken) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Issued issueNewFamily(long userId, String userAgent, String ip) {
        return issue(userId, UUID.randomUUID().toString(), userAgent, ip);
    }

    /** Validates and rotates; reuse of a revoked token revokes its family (committed even though we then fail). */
    @Transactional(noRollbackFor = ApiException.class)
    public Rotated rotate(String rawToken, String userAgent, String ip) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        Instant now = clock.instant();
        RefreshToken current = repository.findByTokenHash(crypto.sha256Hex(rawToken))
                .orElseThrow(() -> new ApiException(ErrorCode.SESSION_EXPIRED));
        if (current.getRevokedAt() != null) {
            // A token rotated moments ago is most likely a second tab racing the first, not theft:
            // reject it, but leave the family alone so the newer cookie keeps working.
            boolean recentRotation = current.getReplacedById() != null
                    && current.getRevokedAt().plus(REUSE_GRACE).isAfter(now);
            if (current.getReplacedById() != null && !recentRotation) {
                log.warn("Refresh token reuse detected for user {}; revoking family {}", current.getUserId(),
                        current.getFamilyId());
                repository.revokeFamily(current.getFamilyId(), now);
            }
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        if (!current.isActiveAt(now)) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        Issued next = issue(current.getUserId(), current.getFamilyId(), userAgent, ip);
        current.revoke(now, next.entity().getId());
        return new Rotated(current.getUserId(), next.rawToken());
    }

    @Transactional
    public void revokeFamilyOf(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHash(crypto.sha256Hex(rawToken))
                .ifPresent(t -> repository.revokeFamily(t.getFamilyId(), clock.instant()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void revokeAllForUser(long userId) {
        repository.revokeAllForUser(userId, clock.instant());
    }

    private Issued issue(long userId, String familyId, String userAgent, String ip) {
        String raw = crypto.randomToken(32);
        Instant now = clock.instant();
        RefreshToken entity = repository.save(new RefreshToken(userId, crypto.sha256Hex(raw), familyId,
                now.plus(properties.refreshTokenTtl()), now, truncate(userAgent, 255), ip));
        return new Issued(raw, entity);
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
