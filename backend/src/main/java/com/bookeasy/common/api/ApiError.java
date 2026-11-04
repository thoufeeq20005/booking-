package com.bookeasy.common.api;

import java.util.List;

/**
 * @param code        stable machine-readable code the frontend maps to a translated message
 * @param message     English fallback message, safe to show to users
 * @param fieldErrors per-field validation problems, empty when not a validation error
 */
public record ApiError(String code, String message, List<FieldError> fieldErrors) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public record FieldError(String field, String code, String message) {
    }
}
