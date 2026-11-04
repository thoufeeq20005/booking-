package com.bookeasy.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.bookeasy.common.config.SecurityProperties;

/**
 * Small, opinionated crypto toolkit: random tokens, SHA-256 token hashing, keyed HMAC for
 * low-entropy codes (OTPs) and AES-256-GCM for secrets that must be recoverable (TOTP seeds).
 */
@Service
public class CryptoService {

    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec aesKey;
    private final SecretKeySpec hmacKey;

    public CryptoService(SecurityProperties properties) {
        byte[] master = decodeKey(properties.dataEncryptionKey());
        // Separate sub-keys per purpose so one key is never used for two algorithms.
        this.aesKey = new SecretKeySpec(hmac(master, "bookeasy-aes"), "AES");
        this.hmacKey = new SecretKeySpec(hmac(master, "bookeasy-hmac"), "HmacSHA256");
    }

    private static byte[] decodeKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY is not set (32 random bytes, base64)");
        }
        byte[] key = Base64.getDecoder().decode(base64.trim());
        if (key.length < 32) {
            throw new IllegalStateException("DATA_ENCRYPTION_KEY must decode to at least 32 bytes");
        }
        return key;
    }

    /** URL-safe random token with the given number of random bytes. */
    public String randomToken(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    public byte[] randomBytes(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return buf;
    }

    /** Uniform random numeric code, e.g. a 6-digit OTP. */
    public String randomDigits(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    /** Hash for high-entropy tokens (refresh, reset, verification links). */
    public String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Keyed hash for low-entropy values so a database leak doesn't allow offline brute force. */
    public String hmacHex(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hmacKey);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public String encrypt(byte[] plaintext) {
        try {
            byte[] iv = randomBytes(GCM_IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext);
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public byte[] decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, all, 0, GCM_IV_BYTES));
            return cipher.doFinal(all, GCM_IV_BYTES, all.length - GCM_IV_BYTES);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    private static byte[] hmac(byte[] key, String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
