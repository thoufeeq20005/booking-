package com.bookeasy.common.text;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.bookeasy.common.exception.ApiException;

/**
 * Translatable text stored as a JSON object keyed by language code. English is required and is the fallback
 * for any language that has no translation.
 */
public final class LocalizedText {

    public static final Set<String> LANGUAGES = Set.of("en", "ta", "hi", "ml", "te", "kn");

    private LocalizedText() {
    }

    /**
     * Trims values, drops blank translations and checks the shape. Returns null for an optional, absent value.
     *
     * @throws ApiException VALIDATION_FAILED on {@code field} when invalid
     */
    public static Map<String, String> clean(String field, Map<String, String> raw, int maxLength, boolean required) {
        if (raw == null || raw.isEmpty()) {
            if (required) {
                throw ApiException.invalidField(field, "NotBlank", "English text is required");
            }
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : raw.entrySet()) {
            if (!LANGUAGES.contains(e.getKey())) {
                throw ApiException.invalidField(field, "UNSUPPORTED_LANGUAGE", "Unsupported language " + e.getKey());
            }
            String v = e.getValue() == null ? "" : e.getValue().strip();
            if (v.length() > maxLength) {
                throw ApiException.invalidField(field, "Size", "At most " + maxLength + " characters");
            }
            if (!v.isEmpty()) {
                out.put(e.getKey(), v);
            }
        }
        if (!out.containsKey("en")) {
            if (!required && out.isEmpty()) {
                return null;
            }
            throw ApiException.invalidField(field, "NotBlank", "English text is required");
        }
        return out;
    }

    /** Text in the requested language, falling back to English. */
    public static String resolve(Map<String, String> text, String language) {
        if (text == null) {
            return null;
        }
        return text.getOrDefault(language, text.get("en"));
    }
}
