package com.bookeasy.common.api;

import java.time.Instant;

/**
 * Envelope used by every endpoint: either {@code data} or {@code error} is present.
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> fail(ApiError error) {
        return new ApiResponse<>(false, null, error, Instant.now());
    }
}
