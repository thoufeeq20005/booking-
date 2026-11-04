package com.bookeasy.auth.support;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

import com.bookeasy.common.exception.ApiException;

/** Minimum 8 characters with a letter and a digit; at most 72 bytes because BCrypt ignores anything longer. */
@Component
public class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    public void validate(String field, String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw ApiException.invalidField(field, "PASSWORD_TOO_SHORT", "Use at least 8 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw ApiException.invalidField(field, "PASSWORD_TOO_LONG", "Use at most 72 bytes");
        }
        boolean letter = password.codePoints().anyMatch(Character::isLetter);
        boolean digit = password.codePoints().anyMatch(Character::isDigit);
        if (!letter || !digit) {
            throw ApiException.invalidField(field, "PASSWORD_TOO_WEAK", "Use letters and numbers");
        }
    }
}
