-- One-time codes can now go to an email address as well as a mobile number (sign in with email + code).
ALTER TABLE otp_verifications
    CHANGE COLUMN phone_e164 target VARCHAR(255) NOT NULL,
    DROP CHECK chk_otp_purpose,
    ADD CONSTRAINT chk_otp_purpose CHECK (purpose IN ('LOGIN', 'VERIFY_PHONE', 'EMAIL_LOGIN'));
