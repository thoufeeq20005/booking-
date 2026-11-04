package com.bookeasy.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes returned to clients. The frontend translates them (see i18n `errors.*`),
 * so renaming a constant is a breaking API change.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Some fields are invalid"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read"),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Please sign in to continue"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "This method is not supported for the resource"),
    CONFLICT(HttpStatus.CONFLICT, "The request conflicts with the current state"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "The uploaded file is too large"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests, please try again later"),
    PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A required external service is not available"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our side"),

    // Authentication
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email or password is incorrect"),
    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. The account is temporarily locked"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "This account has been suspended"),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),
    PHONE_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this mobile number already exists"),
    INVALID_TOKEN(HttpStatus.BAD_REQUEST, "This link is invalid or has already been used"),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "This link has expired"),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again"),
    CSRF_HEADER_MISSING(HttpStatus.FORBIDDEN, "Request rejected"),
    OTP_INVALID(HttpStatus.BAD_REQUEST, "The code is incorrect"),
    OTP_EXPIRED(HttpStatus.BAD_REQUEST, "The code has expired. Request a new one"),
    OTP_RESEND_TOO_SOON(HttpStatus.TOO_MANY_REQUESTS, "Please wait before requesting another code"),
    FULL_NAME_REQUIRED(HttpStatus.BAD_REQUEST, "Enter your name to create an account"),
    TWO_FACTOR_INVALID(HttpStatus.UNAUTHORIZED, "The authentication code is incorrect"),
    TWO_FACTOR_ALREADY_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is already enabled"),
    TWO_FACTOR_NOT_ENABLED(HttpStatus.CONFLICT, "Two-factor authentication is not enabled"),
    TWO_FACTOR_SETUP_REQUIRED(HttpStatus.CONFLICT, "Start two-factor setup first"),
    GOOGLE_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Google sign-in could not be verified"),
    GOOGLE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Google sign-in is not available"),
    BOOTSTRAP_UNAVAILABLE(HttpStatus.NOT_FOUND, "Not found"),
    BOOTSTRAP_ALREADY_DONE(HttpStatus.CONFLICT, "A Super Admin already exists"),

    // Files
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "This file type is not allowed"),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "The file is empty"),

    // Categories
    SLUG_TAKEN(HttpStatus.CONFLICT, "This slug is already in use"),
    CATEGORY_IN_USE(HttpStatus.CONFLICT, "Businesses use this item, so it can only be deactivated"),

    // Organizations
    ORGANIZATION_LIMIT_REACHED(HttpStatus.CONFLICT, "You already have the maximum number of businesses in setup or review"),
    ORGANIZATION_NOT_EDITABLE(HttpStatus.CONFLICT, "This business can't be changed in its current state"),
    ORGANIZATION_INCOMPLETE(HttpStatus.UNPROCESSABLE_ENTITY, "Complete every required step before submitting"),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "This action isn't possible in the business's current status"),
    DOCUMENTS_NOT_ACCEPTED(HttpStatus.CONFLICT, "Every required document must be present and not rejected"),
    BRANCH_LIMIT_REACHED(HttpStatus.CONFLICT, "This business has the maximum number of branches"),

    // Staff
    STAFF_ALREADY_EXISTS(HttpStatus.CONFLICT, "This person is already on the team or invited"),
    INVITATION_EMAIL_MISMATCH(HttpStatus.FORBIDDEN, "Sign in with the email address the invitation was sent to"),

    // Booking
    SLOT_UNAVAILABLE(HttpStatus.CONFLICT, "This time is no longer available"),
    VERIFICATION_REQUIRED(HttpStatus.FORBIDDEN, "Verify your email or mobile number before booking"),
    BOOKING_NOT_CHANGEABLE(HttpStatus.CONFLICT, "This booking can't be changed any more"),
    RESCHEDULE_LIMIT_REACHED(HttpStatus.CONFLICT, "This booking has been rescheduled the maximum number of times"),
    INVALID_QR_CODE(HttpStatus.BAD_REQUEST, "This booking code isn't valid here"),

    // Payments
    PAYMENT_SIGNATURE_INVALID(HttpStatus.BAD_REQUEST, "The payment could not be verified"),
    PAYMENT_NOT_REQUIRED(HttpStatus.CONFLICT, "This booking doesn't need a payment"),
    BOOKING_EXPIRED(HttpStatus.CONFLICT, "The time to pay for this booking has run out"),

    // Reviews
    REVIEW_NOT_ALLOWED(HttpStatus.CONFLICT, "You can review a booking once it is completed"),
    REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "You have already reviewed this booking");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
