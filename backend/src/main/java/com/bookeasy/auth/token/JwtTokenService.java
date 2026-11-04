package com.bookeasy.auth.token;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import com.bookeasy.auth.AuthProperties;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;
import com.bookeasy.user.entity.RoleCode;
import com.bookeasy.user.entity.User;

@Service
public class JwtTokenService {

    public static final String CLAIM_TYPE = "typ";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TOKEN_VERSION = "tv";

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_TWO_FACTOR_CHALLENGE = "2fa_challenge";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final AuthProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtDecoder decoder, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.decoder = decoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedToken(String value, Instant expiresAt) {
    }

    public IssuedToken issueAccessToken(User user) {
        List<String> roles = user.roleSet().stream().map(RoleCode::name).sorted().toList();
        return issue(user, TYPE_ACCESS, properties.accessTokenTtl().toSeconds(), roles);
    }

    /** Short-lived proof that the first factor succeeded; only accepted by the 2FA verification endpoint. */
    public IssuedToken issueTwoFactorChallenge(User user) {
        return issue(user, TYPE_TWO_FACTOR_CHALLENGE, properties.twoFactorChallengeTtl().toSeconds(), null);
    }

    public record TwoFactorChallenge(long userId, int tokenVersion) {
    }

    /** Parses a valid, unexpired challenge token; anything else is reported as an expired session. */
    public TwoFactorChallenge parseTwoFactorChallenge(String token) {
        Jwt jwt = decodeOrThrow(token, ErrorCode.SESSION_EXPIRED);
        Number tokenVersion = jwt.getClaim(CLAIM_TOKEN_VERSION);
        if (!TYPE_TWO_FACTOR_CHALLENGE.equals(jwt.getClaimAsString(CLAIM_TYPE)) || tokenVersion == null) {
            throw new ApiException(ErrorCode.SESSION_EXPIRED);
        }
        return new TwoFactorChallenge(Long.parseLong(jwt.getSubject()), tokenVersion.intValue());
    }

    public Jwt decodeOrThrow(String token, ErrorCode onFailure) {
        try {
            return decoder.decode(token);
        } catch (JwtException e) {
            throw new ApiException(onFailure);
        }
    }

    private IssuedToken issue(User user, String type, long ttlSeconds, List<String> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plusSeconds(ttlSeconds);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim(CLAIM_TYPE, type)
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion());
        if (roles != null) {
            claims.claim(CLAIM_ROLES, roles);
        }
        JwsHeader header = JwsHeader.with(JwtConfig.ALGORITHM).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new IssuedToken(value, expiresAt);
    }
}
