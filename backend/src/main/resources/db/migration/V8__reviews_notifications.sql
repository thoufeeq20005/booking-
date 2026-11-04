-- BookEasy Phase 9: reviews, in-app notifications, delivery outbox, reminders and preferences.

CREATE TABLE reviews (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id  BIGINT        NOT NULL,
    organization_id BIGINT        NOT NULL,
    customer_id     BIGINT        NOT NULL,
    staff_id        BIGINT        NULL,
    rating          TINYINT       NOT NULL,
    comment         VARCHAR(2000) NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'VISIBLE',
    owner_reply     VARCHAR(1000) NULL,
    replied_at      DATETIME(6)   NULL,
    report_count    INT           NOT NULL DEFAULT 0,
    moderation_note VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uk_reviews_appointment UNIQUE (appointment_id),
    CONSTRAINT chk_reviews_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT chk_reviews_status CHECK (status IN ('VISIBLE', 'HIDDEN')),
    INDEX idx_reviews_org (organization_id, status, created_at),
    INDEX idx_reviews_reported (report_count),
    CONSTRAINT fk_reviews_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_reviews_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_reviews_customer FOREIGN KEY (customer_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE review_reports (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    review_id   BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL,
    reason      VARCHAR(300) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    CONSTRAINT uk_review_reports_once UNIQUE (review_id, reporter_id),
    CONSTRAINT fk_review_reports_review FOREIGN KEY (review_id) REFERENCES reviews (id) ON DELETE CASCADE,
    CONSTRAINT fk_review_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- The in-app inbox.
CREATE TABLE notifications (
    id         BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT        NOT NULL,
    type       VARCHAR(40)   NOT NULL,
    title      VARCHAR(200)  NOT NULL,
    body       VARCHAR(1000) NOT NULL,
    link       VARCHAR(255)  NULL,
    read_at    DATETIME(6)   NULL,
    created_at DATETIME(6)   NOT NULL,
    INDEX idx_notifications_user (user_id, created_at),
    INDEX idx_notifications_unread (user_id, read_at),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Outgoing messages, written in the same transaction as the event and sent by a retrying dispatcher.
CREATE TABLE notification_outbox (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT        NULL,
    channel         VARCHAR(16)   NOT NULL,
    destination     VARCHAR(255)  NOT NULL,
    type            VARCHAR(40)   NOT NULL,
    subject         VARCHAR(200)  NOT NULL,
    body            VARCHAR(2000) NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    attempts        INT           NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6)   NOT NULL,
    last_error      VARCHAR(500)  NULL,
    created_at      DATETIME(6)   NOT NULL,
    sent_at         DATETIME(6)   NULL,
    CONSTRAINT chk_outbox_channel CHECK (channel IN ('EMAIL', 'SMS', 'WHATSAPP')),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED')),
    INDEX idx_outbox_due (status, next_attempt_at),
    CONSTRAINT fk_outbox_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE notification_preferences (
    user_id           BIGINT      NOT NULL PRIMARY KEY,
    email_enabled     BOOLEAN     NOT NULL DEFAULT TRUE,
    sms_enabled       BOOLEAN     NOT NULL DEFAULT TRUE,
    whatsapp_enabled  BOOLEAN     NOT NULL DEFAULT FALSE,
    reminders_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at        DATETIME(6) NOT NULL,
    CONSTRAINT fk_notification_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per reminder sent, so a reminder can never go out twice (even with several API instances).
CREATE TABLE appointment_reminders (
    appointment_id BIGINT      NOT NULL,
    offset_minutes INT         NOT NULL,
    sent_at        DATETIME(6) NOT NULL,
    PRIMARY KEY (appointment_id, offset_minutes),
    CONSTRAINT fk_reminders_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
