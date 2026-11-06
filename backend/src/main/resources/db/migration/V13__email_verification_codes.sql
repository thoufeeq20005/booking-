-- Email addresses are now verified with a 6-digit code (same rules as SMS codes) instead of an emailed link.
ALTER TABLE otp_verifications
    DROP CHECK chk_otp_purpose,
    ADD CONSTRAINT chk_otp_purpose CHECK (purpose IN ('LOGIN', 'VERIFY_PHONE', 'EMAIL_LOGIN', 'VERIFY_EMAIL'));
