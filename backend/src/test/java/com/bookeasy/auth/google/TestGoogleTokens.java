package com.bookeasy.auth.google;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;

import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/** Mints Google-shaped ID tokens with a local RSA key, and builds a verifier that trusts that key. */
public class TestGoogleTokens {

    public static final String CLIENT_ID = "test-client-id.apps.googleusercontent.com";

    private final KeyPair keyPair;
    private final Clock clock;

    public TestGoogleTokens(Clock clock) {
        this.clock = clock;
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            this.keyPair = gen.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public GoogleIdTokenVerifier verifier() {
        return new NimbusGoogleIdTokenVerifier(CLIENT_ID,
                NimbusJwtDecoder.withPublicKey((RSAPublicKey) keyPair.getPublic()).build(), clock);
    }

    public String token(String subject, String email, boolean emailVerified, String name) {
        return token(subject, email, emailVerified, name, CLIENT_ID, "https://accounts.google.com");
    }

    public String token(String subject, String email, boolean emailVerified, String name, String audience,
                        String issuer) {
        Instant now = clock.instant();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .audience(audience)
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(3600)))
                .claim("email", email)
                .claim("email_verified", emailVerified)
                .claim("name", name)
                .build();
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
            jwt.sign(new RSASSASigner(keyPair.getPrivate()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }
}
