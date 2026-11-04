package com.bookeasy.auth.google;

/** Verifies a Google Identity Services ID token (signature, issuer, audience, expiry). */
public interface GoogleIdTokenVerifier {

    record GoogleIdentity(String subject, String email, boolean emailVerified, String name) {
    }

    /** @throws com.bookeasy.common.exception.ApiException GOOGLE_TOKEN_INVALID or GOOGLE_NOT_CONFIGURED */
    GoogleIdentity verify(String idToken);
}
