package com.bookeasy.support;

import java.io.ByteArrayOutputStream;

/** RFC 4648 Base32 decoding, used by tests to act like an authenticator app. */
public final class Base32Decoder {

    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Base32Decoder() {
    }

    public static byte[] decode(String input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : input.replace("=", "").toUpperCase().toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("Invalid Base32 character " + c);
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xff);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
