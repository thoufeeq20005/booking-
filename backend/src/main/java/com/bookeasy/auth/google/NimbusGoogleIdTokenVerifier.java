package com.bookeasy.auth.google;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.bookeasy.auth.AuthProperties;
import com.bookeasy.common.exception.ApiException;
import com.bookeasy.common.exception.ErrorCode;

/** Verifies Google ID tokens against Google's published JWKS (keys are cached by Nimbus). */
@Component
public class NimbusGoogleIdTokenVerifier implements GoogleIdTokenVerifier {

    static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final JwtDecoder decoder;

    @Autowired
    public NimbusGoogleIdTokenVerifier(AuthProperties properties, Clock clock) {
        this(properties.google().clientId(), NimbusJwtDecoder.withJwkSetUri(JWKS_URI).build(), clock);
    }

    /** Visible for tests: accepts a decoder backed by a local key set. */
    NimbusGoogleIdTokenVerifier(String clientId, NimbusJwtDecoder nimbus, Clock clock) {
        if (!StringUtils.hasText(clientId)) {
            this.decoder = null;
            return;
        }
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ofSeconds(30));
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(List.of(
                timestamps,
                new JwtClaimValidator<String>("iss", ISSUERS::contains),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(clientId))));
        nimbus.setJwtValidator(validator);
        this.decoder = nimbus;
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (decoder == null) {
            throw new ApiException(ErrorCode.GOOGLE_NOT_CONFIGURED);
        }
        Jwt jwt;
        try {
            jwt = decoder.decode(idToken);
        } catch (JwtException e) {
            throw new ApiException(ErrorCode.GOOGLE_TOKEN_INVALID);
        }
        Object verified = jwt.getClaims().get("email_verified");
        boolean emailVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        return new GoogleIdentity(jwt.getSubject(), jwt.getClaimAsString("email"), emailVerified,
                jwt.getClaimAsString("name"));
    }
}
