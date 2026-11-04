-- BookEasy Phase 7: appointments and the double-booking guard.

CREATE TABLE appointments (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    reference        VARCHAR(12)   NOT NULL,
    customer_id      BIGINT        NOT NULL,
    organization_id  BIGINT        NOT NULL,
    branch_id        BIGINT        NOT NULL,
    service_id       BIGINT        NOT NULL,
    staff_id         BIGINT        NULL,
    starts_at        DATETIME(6)   NOT NULL,
    ends_at          DATETIME(6)   NOT NULL,
    -- ends_at + the service's buffer: the resource stays blocked until then.
    blocked_until    DATETIME(6)   NOT NULL,
    status           VARCHAR(20)   NOT NULL,
    service_name     VARCHAR(150)  NOT NULL,
    service_price    DECIMAL(12, 2) NOT NULL,
    platform_fee     DECIMAL(12, 2) NOT NULL,
    total_amount     DECIMAL(12, 2) NOT NULL,
    customer_note    VARCHAR(500)  NULL,
    cancel_reason    VARCHAR(500)  NULL,
    cancelled_by     VARCHAR(16)   NULL,
    cancelled_at     DATETIME(6)   NULL,
    refund_amount    DECIMAL(12, 2) NULL,
    checked_in_at    DATETIME(6)   NULL,
    completed_at     DATETIME(6)   NULL,
    reschedule_count INT           NOT NULL DEFAULT 0,
    created_at       DATETIME(6)   NOT NULL,
    updated_at       DATETIME(6)   NOT NULL,
    version          BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_appointments_reference UNIQUE (reference),
    CONSTRAINT chk_appointments_status CHECK (status IN ('PENDING_PAYMENT', 'PENDING_APPROVAL', 'CONFIRMED', 'CANCELLED',
        'DECLINED', 'COMPLETED', 'NO_SHOW', 'EXPIRED')),
    CONSTRAINT chk_appointments_time CHECK (starts_at < ends_at AND ends_at <= blocked_until),
    CONSTRAINT chk_appointments_money CHECK (service_price >= 0 AND platform_fee >= 0 AND total_amount >= 0),
    INDEX idx_appointments_customer (customer_id, starts_at),
    INDEX idx_appointments_org_day (organization_id, starts_at),
    INDEX idx_appointments_staff_day (staff_id, starts_at),
    INDEX idx_appointments_branch_day (branch_id, starts_at),
    CONSTRAINT fk_appointments_customer FOREIGN KEY (customer_id) REFERENCES users (id),
    CONSTRAINT fk_appointments_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_appointments_branch FOREIGN KEY (branch_id) REFERENCES organization_branches (id),
    CONSTRAINT fk_appointments_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_appointments_staff FOREIGN KEY (staff_id) REFERENCES staff_profiles (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per 5-minute unit a booking occupies on a resource ("staff:<id>" or "svc:<branch>:<service>").
-- The primary key makes overlapping bookings impossible, even across API instances and concurrent requests.
CREATE TABLE appointment_slot_locks (
    resource_key   VARCHAR(64) NOT NULL,
    slot_start     DATETIME(6) NOT NULL,
    appointment_id BIGINT      NOT NULL,
    PRIMARY KEY (resource_key, slot_start),
    INDEX idx_slot_locks_appointment (appointment_id),
    CONSTRAINT fk_slot_locks_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO platform_settings (setting_key, setting_value, value_type, description, is_public) VALUES
    ('refund.full_before_hours',    '24', 'INTEGER', 'Customer cancellations at least this many hours ahead get a full refund of the service fee', TRUE),
    ('refund.partial_before_hours', '2',  'INTEGER', 'Customer cancellations at least this many hours ahead get the partial refund', TRUE),
    ('refund.partial_percent',      '50', 'INTEGER', 'Percent of the service fee refunded in the partial window', TRUE),
    ('booking.max_reschedules',     '2',  'INTEGER', 'How many times a customer may reschedule one booking', TRUE);
