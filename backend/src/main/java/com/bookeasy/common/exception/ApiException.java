package com.bookeasy.common.exception;

import java.util.List;

import com.bookeasy.common.api.ApiError;

/**
 * Business-rule failure that maps directly to an HTTP response. Throw from services; never catch to hide.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ApiError.FieldError> fieldErrors;

    public ApiException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public ApiException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    private ApiException(ErrorCode errorCode, String message, List<ApiError.FieldError> fieldErrors) {
        super(message);
        this.errorCode = errorCode;
        this.fieldErrors = fieldErrors;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public List<ApiError.FieldError> fieldErrors() {
        return fieldErrors;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    /** A rule failure that lists several problems, e.g. every missing step of an application. */
    public static ApiException withFields(ErrorCode code, List<ApiError.FieldError> fieldErrors) {
        return new ApiException(code, code.defaultMessage(), List.copyOf(fieldErrors));
    }

    /** A single-field validation failure raised by service-level rules (e.g. password policy). */
    public static ApiException invalidField(String field, String code, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, ErrorCode.VALIDATION_FAILED.defaultMessage(),
                List.of(new ApiError.FieldError(field, code, message)));
    }
}
