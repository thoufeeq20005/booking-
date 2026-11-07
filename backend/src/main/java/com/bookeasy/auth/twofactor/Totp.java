package com.bookeasy.auth.twofactor;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.OptionalLong;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (HMAC-SHA1, 30 s steps, 6 digits) – the defaults every authenticator app supports. */
public final class Totp {

    public static final int DIGITS = 6;
    public static final long STEP_SECONDS = 30;
    private static final int[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};

    private Totp() {
    }

    public static long stepAt(Instant time) {
        return Math.floorDiv(time.getEpochSecond(), STEP_SECONDS);
    }

    public static String codeAt(byte[] secret, long step, int digits) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            String code = Integer.toString(binary % POW10[digits]);
            return "0".repeat(digits - code.length()) + code;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Accepts the current step and one step either side (clock drift). Returns the matching step so the
     * caller can reject replays of the same or an earlier code.
     */
    public static OptionalLong verify(byte[] secret, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = stepAt(now);
        for (long step = current - 1; step <= current + 1; step++) {
            if (java.security.MessageDigest.isEqual(codeAt(secret, step, DIGITS).getBytes(), code.getBytes())) {
                return OptionalLong.of(step);
            }
        }
        return OptionalLong.empty();
    }
}
