package com.bookeasy.organization.service;

import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;

import com.bookeasy.auth.support.Identifiers;
import com.bookeasy.common.exception.ApiException;

/** Normalises free-text form input: trims, turns blanks into null and canonicalises phones, URLs and GSTINs. */
final class Inputs {

    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private Inputs() {
    }

    static String text(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        return s.isEmpty() ? null : s;
    }

    static String email(String raw) {
        String s = text(raw);
        return s == null ? null : Identifiers.normalizeEmail(s);
    }

    static String phone(String field, String raw) {
        String s = text(raw);
        if (s == null) {
            return null;
        }
        return Identifiers.normalizeIndianMobile(s)
                .orElseThrow(() -> ApiException.invalidField(field, "INVALID_PHONE", "Enter a valid Indian mobile number"));
    }

    /** Only absolute http(s) URLs with a host, so a stored link can never be a javascript: URL. */
    static String website(String raw) {
        String s = text(raw);
        if (s == null) {
            return null;
        }
        if (!s.matches("(?i)^https?://.*")) {
            s = "https://" + s;
        }
        try {
            URI uri = new URI(s);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("http") || scheme.equals("https")) || uri.getHost() == null
                    || !uri.getHost().contains(".")) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (Exception e) {
            throw ApiException.invalidField("website", "INVALID_URL", "Enter a valid website address");
        }
    }

    static String gstin(String raw) {
        String s = text(raw);
        if (s == null) {
            return null;
        }
        s = s.toUpperCase(Locale.ROOT);
        if (!GSTIN.matcher(s).matches()) {
            throw ApiException.invalidField("gstin", "INVALID_GSTIN", "Enter a valid 15-character GSTIN");
        }
        return s;
    }

    static String timezone(String raw) {
        String s = text(raw);
        if (s == null) {
            return "Asia/Kolkata";
        }
        try {
            return ZoneId.of(s).getId();
        } catch (DateTimeException e) {
            throw ApiException.invalidField("timezone", "INVALID_TIMEZONE", "Unknown timezone");
        }
    }
}
