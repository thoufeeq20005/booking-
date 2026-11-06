package com.bookeasy.auth.entity;

public enum OtpPurpose {
    /** Sign in or sign up with a mobile number. */
    LOGIN,
    /** Attach and verify a mobile number on an existing, signed-in account. */
    VERIFY_PHONE,
    /** Sign in to an existing account with a code sent to its email address. */
    EMAIL_LOGIN
}
