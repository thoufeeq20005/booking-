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
        scanned = fromPassLink(scanned);
        if (scanned == null || !scanned.startsWith(PREFIX)) {
            return null;
        }
        String[] parts = scanned.trim().substring(PREFIX.length()).split(":");
        if (parts.length != 2) {
            return null;
        }
        return crypto.constantTimeEquals(signature(parts[0]), parts[1]) ? parts[0] : null;
    }

    /**
     * The customer's QR holds a link to the public pass page ({@code https://site/pass/<payload>}) so a phone camera
     * opens the booking details; staff can paste either the link or the bare payload at check-in.
     */
    static String fromPassLink(String scanned) {
        if (scanned == null) {
            return null;
        }
        String s = scanned.trim();
        int i = s.indexOf("/pass/");
        if (i < 0) {
            return s;
        }
        String tail = s.substring(i + "/pass/".length());
        int end = tail.indexOf('?');
        if (end >= 0) {
            tail = tail.substring(0, end);
        }
        try {
            return java.net.URLDecoder.decode(tail, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String signature(String reference) {
        return crypto.hmacHex("qr:" + reference).substring(0, 20);
    }
}
