package com.bookeasy.common.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** URL slugs: lowercase ASCII letters, digits and single hyphens. */
public final class Slugs {

    public static final Pattern VALID = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    private Slugs() {
    }

    /** Best-effort slug from free text. Returns {@code fallback} when nothing ASCII remains (e.g. Tamil names). */
    public static String from(String text, int maxLength, String fallback) {
        String s = text == null ? "" : Normalizer.normalize(text, Normalizer.Form.NFD);
        s = DIACRITICS.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
        s = NON_ALNUM.matcher(s).replaceAll("-").replaceAll("^-+|-+$", "");
        if (s.length() > maxLength) {
            s = s.substring(0, maxLength).replaceAll("-+$", "");
        }
        return s.isEmpty() ? fallback : s;
    }
}
