-- BookEasy Phase 1: platform foundation.
-- Conventions: utf8mb4, InnoDB, BIGINT surrogate keys, DATETIME(6) stored in UTC,
-- money as DECIMAL(12,2). Later modules add their tables in their own migrations.

CREATE TABLE roles (
    id          SMALLINT     NOT NULL PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    CONSTRAINT uk_roles_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO roles (id, code, description) VALUES
    (1, 'CUSTOMER',       'Books and manages appointments'),
    (2, 'BUSINESS_OWNER', 'Owns organizations, branches, services and staff'),
    (3, 'STAFF',          'Serves appointments within assigned branches'),
    (4, 'SUPER_ADMIN',    'Full platform administration');

-- Typed key/value settings editable by Super Admin (commission, refund, retention, locales ...).
CREATE TABLE platform_settings (
    setting_key   VARCHAR(100)  NOT NULL PRIMARY KEY,
    setting_value VARCHAR(1000) NOT NULL,
    value_type    VARCHAR(16)   NOT NULL,
    description   VARCHAR(255)  NOT NULL,
    is_public     BOOLEAN       NOT NULL DEFAULT FALSE,
    updated_at    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by    BIGINT        NULL,
    CONSTRAINT chk_platform_settings_type CHECK (value_type IN ('STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'LIST'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO platform_settings (setting_key, setting_value, value_type, description, is_public) VALUES
    ('commission.percent',             '5.00',                    'DECIMAL', 'BookEasy platform fee as a percent of the original service fee', TRUE),
    ('currency.code',                  'INR',                     'STRING',  'Platform currency (INR only)',                                   TRUE),
    ('locale.supported',               'en,ta,hi,ml,te,kn',       'LIST',    'Supported UI languages',                                         TRUE),
    ('locale.default',                 'en',                      'STRING',  'Default UI language',                                            TRUE),
    ('timezone.default',               'Asia/Kolkata',            'STRING',  'Default timezone for organizations',                             TRUE),
    ('retention.booking_history_years','2',                       'INTEGER', 'Customer booking history retention (1-3 years)',                 FALSE),
    ('reminder.default_offsets_minutes','1440,60',                'LIST',    'Default appointment reminder offsets in minutes',                FALSE);

-- Single-row customer support configuration. Placeholders until the owner supplies real details.
CREATE TABLE support_settings (
    id              TINYINT      NOT NULL PRIMARY KEY,
    support_email   VARCHAR(255) NULL,
    support_phone   VARCHAR(20)  NULL,
    working_days    VARCHAR(64)  NOT NULL,
    opens_at        TIME         NOT NULL,
    closes_at       TIME         NOT NULL,
    timezone        VARCHAR(64)  NOT NULL,
    is_available    BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    updated_by      BIGINT       NULL,
    CONSTRAINT chk_support_settings_singleton CHECK (id = 1)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO support_settings (id, support_email, support_phone, working_days, opens_at, closes_at, timezone)
VALUES (1, NULL, NULL, 'MON,TUE,WED,THU,FRI,SAT', '09:00:00', '18:00:00', 'Asia/Kolkata');

-- Append-only log of sensitive actions (approvals, suspensions, refunds, financial/permission changes).
CREATE TABLE audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    actor_user_id BIGINT       NULL,
    actor_role    VARCHAR(32)  NULL,
    action        VARCHAR(64)  NOT NULL,
    entity_type   VARCHAR(64)  NOT NULL,
    entity_id     VARCHAR(64)  NULL,
    details       JSON         NULL,
    ip_address    VARCHAR(45)  NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_audit_logs_entity (entity_type, entity_id),
    INDEX idx_audit_logs_actor (actor_user_id, created_at),
    INDEX idx_audit_logs_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
