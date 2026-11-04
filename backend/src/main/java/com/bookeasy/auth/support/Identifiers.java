package com.bookeasy.auth.support;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/** Canonical forms for login identifiers, so lookups and unique constraints agree. */
public final class Identifiers {

    // Indian mobile numbers: 10 digits starting 6-9, optionally prefixed with +91, 91 or 0.
    private static final Pattern INDIAN_MOBILE = Pattern.compile("^(?:\\+?91|0)?([6-9]\\d{9})$");

    private Identifiers() {
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Returns +91XXXXXXXXXX, or empty when the input is not a valid Indian mobile number. */
    public static Optional<String> normalizeIndianMobile(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String compact = raw.replaceAll("[\\s()-]", "");
        var m = INDIAN_MOBILE.matcher(compact);
        return m.matches() ? Optional.of("+91" + m.group(1)) : Optional.empty();
    }
}
