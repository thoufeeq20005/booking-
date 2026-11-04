-- BookEasy Phase 5: services, staff, availability and leave.
-- Owner-written names and descriptions are plain text in the owner's language (like organizations.description).
-- Slots are never stored: they are computed from branch hours ∩ staff availability − breaks − holidays − leave − bookings.

CREATE TABLE services (
    id                     BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id        BIGINT        NOT NULL,
    name                   VARCHAR(150)  NOT NULL,
    description            VARCHAR(1000) NULL,
    duration_minutes       INT           NOT NULL,
    price                  DECIMAL(12, 2) NOT NULL,
    consultation_type      VARCHAR(16)   NOT NULL DEFAULT 'IN_PERSON',
    requires_staff         BOOLEAN       NOT NULL DEFAULT TRUE,
    -- Free time kept after each appointment (cleaning, notes).
    buffer_minutes         INT           NOT NULL DEFAULT 0,
    -- Distance between offered start times, aligned to the clock (e.g. 15 → 09:00, 09:15 …).
    slot_interval_minutes  INT           NOT NULL DEFAULT 15,
    max_advance_days       INT           NOT NULL DEFAULT 60,
    min_notice_minutes     INT           NOT NULL DEFAULT 60,
    is_active              BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT chk_services_duration CHECK (duration_minutes BETWEEN 5 AND 720),
    CONSTRAINT chk_services_price CHECK (price >= 0),
    CONSTRAINT chk_services_type CHECK (consultation_type IN ('IN_PERSON', 'ONLINE')),
    CONSTRAINT chk_services_buffer CHECK (buffer_minutes BETWEEN 0 AND 240),
    CONSTRAINT chk_services_interval CHECK (slot_interval_minutes BETWEEN 5 AND 240),
    CONSTRAINT chk_services_advance CHECK (max_advance_days BETWEEN 1 AND 365),
    CONSTRAINT chk_services_notice CHECK (min_notice_minutes BETWEEN 0 AND 20160),
    INDEX idx_services_org (organization_id, is_active),
    CONSTRAINT fk_services_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Which branches offer a service; NULL price_override means the service price applies.
CREATE TABLE branch_services (
    branch_id      BIGINT         NOT NULL,
    service_id     BIGINT         NOT NULL,
    price_override DECIMAL(12, 2) NULL,
    PRIMARY KEY (branch_id, service_id),
    CONSTRAINT chk_branch_services_price CHECK (price_override IS NULL OR price_override >= 0),
    INDEX idx_branch_services_service (service_id),
    CONSTRAINT fk_branch_services_branch FOREIGN KEY (branch_id) REFERENCES organization_branches (id) ON DELETE CASCADE,
    CONSTRAINT fk_branch_services_service FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per person per organization. user_id is NULL while an invitation is open (enforced by StaffService;
-- MySQL forbids CHECKs on columns with a foreign-key referential action).
CREATE TABLE staff_profiles (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    user_id         BIGINT       NULL,
    display_name    VARCHAR(120) NOT NULL,
    title           VARCHAR(40)  NULL,
    specialisation  VARCHAR(150) NULL,
    permissions     JSON         NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'INVITED',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT uk_staff_profiles_user UNIQUE (organization_id, user_id),
    CONSTRAINT chk_staff_profiles_status CHECK (status IN ('INVITED', 'ACTIVE', 'INACTIVE')),
    INDEX idx_staff_profiles_user (user_id),
    CONSTRAINT fk_staff_profiles_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE staff_branches (
    staff_id  BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    PRIMARY KEY (staff_id, branch_id),
    INDEX idx_staff_branches_branch (branch_id),
    CONSTRAINT fk_staff_branches_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_branches_branch FOREIGN KEY (branch_id) REFERENCES organization_branches (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE staff_services (
    staff_id   BIGINT NOT NULL,
    service_id BIGINT NOT NULL,
    PRIMARY KEY (staff_id, service_id),
    INDEX idx_staff_services_service (service_id),
    CONSTRAINT fk_staff_services_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_services_service FOREIGN KEY (service_id) REFERENCES services (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Weekly working windows per staff member and branch (branch local time). Several windows per day are allowed.
CREATE TABLE staff_availability (
    id          BIGINT  NOT NULL AUTO_INCREMENT PRIMARY KEY,
    staff_id    BIGINT  NOT NULL,
    branch_id   BIGINT  NOT NULL,
    day_of_week TINYINT NOT NULL,
    start_time  TIME    NOT NULL,
    end_time    TIME    NOT NULL,
    CONSTRAINT chk_staff_availability_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_staff_availability_order CHECK (start_time < end_time),
    INDEX idx_staff_availability_lookup (branch_id, day_of_week),
    INDEX idx_staff_availability_staff (staff_id),
    CONSTRAINT fk_staff_availability_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_availability_branch FOREIGN KEY (branch_id) REFERENCES organization_branches (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE staff_leaves (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    staff_id   BIGINT       NOT NULL,
    starts_at  DATETIME(6)  NOT NULL,
    ends_at    DATETIME(6)  NOT NULL,
    reason     VARCHAR(200) NULL,
    created_by BIGINT       NULL,
    created_at DATETIME(6)  NOT NULL,
    CONSTRAINT chk_staff_leaves_order CHECK (starts_at < ends_at),
    INDEX idx_staff_leaves_range (staff_id, starts_at, ends_at),
    CONSTRAINT fk_staff_leaves_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_leaves_creator FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Email invitations. The raw token only exists in the email; we keep its SHA-256.
CREATE TABLE staff_invitations (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    staff_profile_id BIGINT       NOT NULL,
    email            VARCHAR(255) NOT NULL,
    token_hash       CHAR(64)     NOT NULL,
    expires_at       DATETIME(6)  NOT NULL,
    accepted_at      DATETIME(6)  NULL,
    revoked_at       DATETIME(6)  NULL,
    invited_by       BIGINT       NULL,
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_staff_invitations_token UNIQUE (token_hash),
    INDEX idx_staff_invitations_profile (staff_profile_id),
    INDEX idx_staff_invitations_email (email),
    CONSTRAINT fk_staff_invitations_profile FOREIGN KEY (staff_profile_id) REFERENCES staff_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_staff_invitations_inviter FOREIGN KEY (invited_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
