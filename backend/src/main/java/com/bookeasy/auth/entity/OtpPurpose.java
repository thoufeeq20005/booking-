package com.bookeasy.auth.entity;

public enum OtpPurpose {
    /** Former mobile-number sign-in (removed); kept so older stored rows still map. */
    LOGIN,
    /** Attach and verify a mobile number on an existing, signed-in account. */
    VERIFY_PHONE,
    /** Sign in to an existing account with a code sent to its email address. */
    EMAIL_LOGIN,
    /** Confirm that the account's email address belongs to the user. */
    VERIFY_EMAIL
}
