package com.bookeasy.appointment;

import org.springframework.stereotype.Component;

import com.bookeasy.common.crypto.CryptoService;

/**
 * The booking pass: {@code BOOKEASY:<reference>:<signature>}. The signature is an HMAC of the reference under the
 * server key, so passes can't be forged or guessed from a reference, and nothing extra needs storing.
 */
@Component
public class QrCodes {

    private static final String PREFIX = "BOOKEASY:";

    private final CryptoService crypto;

    public QrCodes(CryptoService crypto) {
        this.crypto = crypto;
    }

    public String payload(String reference) {
        return PREFIX + reference + ":" + signature(reference);
    }

    /** The reference inside a valid pass, or null. Accepts the bare reference only together with its signature. */
    public String verify(String scanned) {
        if (scanned == null || !scanned.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = scanned.trim().substring(PREFIX.length()).split(":");
        if (parts.length != 2) {
            return null;
        }
        return crypto.constantTimeEquals(signature(parts[0]), parts[1]) ? parts[0] : null;
    }

    private String signature(String reference) {
        return crypto.hmacHex("qr:" + reference).substring(0, 20);
    }
}
