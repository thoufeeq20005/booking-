-- BookEasy Phase 10: family members, favourites, saved locations, support requests and data retention.

CREATE TABLE family_members (
    id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT       NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    relation      VARCHAR(16)  NOT NULL,
    date_of_birth DATE         NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT chk_family_relation CHECK (relation IN ('SPOUSE', 'CHILD', 'PARENT', 'SIBLING', 'OTHER')),
    INDEX idx_family_user (user_id),
    CONSTRAINT fk_family_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Who the appointment is for when it isn't the account holder (name kept even if the family entry is removed).
ALTER TABLE appointments
    ADD COLUMN family_member_id BIGINT NULL,
    ADD COLUMN patient_name VARCHAR(120) NULL,
    ADD CONSTRAINT fk_appointments_family FOREIGN KEY (family_member_id) REFERENCES family_members (id) ON DELETE SET NULL;

CREATE TABLE favourites (
    user_id         BIGINT      NOT NULL,
    organization_id BIGINT      NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, organization_id),
    CONSTRAINT fk_favourites_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_favourites_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE saved_locations (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    label      VARCHAR(60)   NOT NULL,
    latitude   DECIMAL(9, 6) NOT NULL,
    longitude  DECIMAL(9, 6) NOT NULL,
    created_at DATETIME(6)   NOT NULL,
    INDEX idx_saved_locations_user (user_id),
    CONSTRAINT fk_saved_locations_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE support_requests (
    id             BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT        NOT NULL,
    appointment_id BIGINT        NULL,
    subject        VARCHAR(150)  NOT NULL,
    message        VARCHAR(4000) NOT NULL,
    status         VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    admin_reply    VARCHAR(4000) NULL,
    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    resolved_at    DATETIME(6)   NULL,
    CONSTRAINT chk_support_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'RESOLVED')),
    INDEX idx_support_user (user_id, created_at),
    INDEX idx_support_status (status, created_at),
    CONSTRAINT fk_support_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_support_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE data_retention_runs (
    id                        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    started_at                DATETIME(6) NOT NULL,
    finished_at               DATETIME(6) NULL,
    appointments_anonymised   INT         NOT NULL DEFAULT 0,
    notifications_deleted     INT         NOT NULL DEFAULT 0,
    outbox_deleted            INT         NOT NULL DEFAULT 0,
    webhook_events_deleted    INT         NOT NULL DEFAULT 0,
    rate_limits_deleted       INT         NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
