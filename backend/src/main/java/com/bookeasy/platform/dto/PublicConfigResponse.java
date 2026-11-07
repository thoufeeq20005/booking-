package com.bookeasy.platform.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/** Non-secret platform configuration the frontend needs before sign-in. */
public record PublicConfigResponse(
        String currency,
        BigDecimal commissionPercent,
        List<String> supportedLanguages,
        String defaultLanguage,
        String timezone,
        Support support) {

    /** Email/phone are null until Super Admin configures real contact details. */
    public record Support(
            String email,
            String phone,
            List<String> workingDays,
            LocalTime opensAt,
            LocalTime closesAt,
            String timezone,
            boolean available) {
    }
}
