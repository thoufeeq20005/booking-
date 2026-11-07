-- BookEasy Phase 2: accounts and authentication.
-- All tokens/codes are stored as SHA-256 / HMAC hashes, never in plain text.

CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    full_name             VARCHAR(120) NOT NULL,
    email                 VARCHAR(255) NULL,
    phone_e164            VARCHAR(16)  NULL,
    password_hash         VARCHAR(100) NULL,
    status                VARCHAR(24)  NOT NULL DEFAULT 'ACTIVE',
    email_verified_at     DATETIME(6)  NULL,
    phone_verified_at     DATETIME(6)  NULL,
    preferred_language    VARCHAR(5)   NOT NULL DEFAULT 'en',
    two_factor_enabled    BOOLEAN      NOT NULL DEFAULT FALSE,
    two_factor_secret_enc VARCHAR(255) NULL,
    two_factor_last_step  BIGINT       NULL,
    failed_login_count    INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME(6)  NULL,
    -- Incremented to invalidate every access token issued before (logout-all, password reset, suspension).
    token_version         INT          NOT NULL DEFAULT 0,
    last_login_at         DATETIME(6)  NULL,
    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone_e164),
    CONSTRAINT chk_users_contact CHECK (email IS NOT NULL OR phone_e164 IS NOT NULL),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT chk_users_language CHECK (preferred_language IN ('en', 'ta', 'hi', 'ml', 'te', 'kn')),
    INDEX idx_users_status (status),
    INDEX idx_users_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id    BIGINT      NOT NULL,
    role_id    SMALLINT    NOT NULL,
    PRIMARY KEY (user_id, role_id),
    INDEX idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT       NOT NULL,
    token_hash     CHAR(64)     NOT NULL,
    -- All tokens descending from one login share a family; reuse of a rotated token revokes the family.
    family_id      CHAR(36)     NOT NULL,
    expires_at     DATETIME(6)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    revoked_at     DATETIME(6)  NULL,
    replaced_by_id BIGINT       NULL,
    user_agent     VARCHAR(255) NULL,
    ip_address     VARCHAR(45)  NULL,
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    INDEX idx_refresh_tokens_user (user_id),
    INDEX idx_refresh_tokens_family (family_id),
    INDEX idx_refresh_tokens_expires (expires_at),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE email_verifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    email      VARCHAR(255) NOT NULL,
    token_hash CHAR(64)     NOT NULL,
    expires_at DATETIME(6)  NOT NULL,
    used_at    DATETIME(6)  NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT uk_email_verifications_hash UNIQUE (token_hash),
    INDEX idx_email_verifications_user (user_id),
    INDEX idx_email_verifications_expires (expires_at),
    CONSTRAINT fk_email_verifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    token_hash CHAR(64)    NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_password_reset_hash UNIQUE (token_hash),
    INDEX idx_password_reset_user (user_id),
    INDEX idx_password_reset_expires (expires_at),
    CONSTRAINT fk_password_reset_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE otp_verifications (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    phone_e164  VARCHAR(16) NOT NULL,
    purpose     VARCHAR(24) NOT NULL,
    code_hash   CHAR(64)    NOT NULL,
    attempts    INT         NOT NULL DEFAULT 0,
    expires_at  DATETIME(6) NOT NULL,
    consumed_at DATETIME(6) NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT chk_otp_purpose CHECK (purpose IN ('LOGIN', 'VERIFY_PHONE')),
    INDEX idx_otp_phone_purpose (phone_e164, purpose, created_at),
    INDEX idx_otp_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE oauth_accounts (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    provider         VARCHAR(24)  NOT NULL,
    provider_subject VARCHAR(255) NOT NULL,
    email            VARCHAR(255) NULL,
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_oauth_provider_subject UNIQUE (provider, provider_subject),
    INDEX idx_oauth_user (user_id),
    CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Fixed-window counters shared by every API instance (login, OTP, reset and bootstrap throttling).
CREATE TABLE rate_limit_counters (
    bucket_key   VARCHAR(191) NOT NULL,
    window_start DATETIME(6)  NOT NULL,
    hits         INT          NOT NULL,
    PRIMARY KEY (bucket_key, window_start),
    INDEX idx_rate_limit_window (window_start)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE platform_settings
    ADD CONSTRAINT fk_platform_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE support_settings
    ADD CONSTRAINT fk_support_settings_updated_by FOREIGN KEY (updated_by) REFERENCES users (id) ON DELETE SET NULL;
