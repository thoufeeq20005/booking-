-- BookEasy Phase 8: Razorpay payments, refunds, webhooks, commission and settlements. Money in paise (BIGINT).

CREATE TABLE payments (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id      BIGINT       NOT NULL,
    razorpay_order_id   VARCHAR(64)  NOT NULL,
    razorpay_payment_id VARCHAR(64)  NULL,
    amount_paise        BIGINT       NOT NULL,
    currency            CHAR(3)      NOT NULL DEFAULT 'INR',
    status              VARCHAR(16)  NOT NULL,
    method              VARCHAR(32)  NULL,
    captured_at         DATETIME(6)  NULL,
    created_at          DATETIME(6)  NOT NULL,
    updated_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uk_payments_order UNIQUE (razorpay_order_id),
    CONSTRAINT uk_payments_payment UNIQUE (razorpay_payment_id),
    CONSTRAINT chk_payments_status CHECK (status IN ('CREATED', 'CAPTURED', 'FAILED')),
    CONSTRAINT chk_payments_amount CHECK (amount_paise > 0),
    INDEX idx_payments_appointment (appointment_id),
    CONSTRAINT fk_payments_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refunds (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    payment_id         BIGINT       NOT NULL,
    appointment_id     BIGINT       NOT NULL,
    amount_paise       BIGINT       NOT NULL,
    status             VARCHAR(16)  NOT NULL,
    razorpay_refund_id VARCHAR(64)  NULL,
    attempts           INT          NOT NULL DEFAULT 0,
    last_error         VARCHAR(500) NULL,
    created_at         DATETIME(6)  NOT NULL,
    processed_at       DATETIME(6)  NULL,
    CONSTRAINT uk_refunds_razorpay UNIQUE (razorpay_refund_id),
    CONSTRAINT uk_refunds_appointment UNIQUE (appointment_id),
    CONSTRAINT chk_refunds_status CHECK (status IN ('PENDING', 'REQUESTED', 'PROCESSED', 'FAILED')),
    CONSTRAINT chk_refunds_amount CHECK (amount_paise > 0),
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id),
    CONSTRAINT fk_refunds_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Every webhook event processed once: Razorpay retries deliveries, so the event id is the idempotency key.
CREATE TABLE webhook_events (
    event_id    VARCHAR(64)  NOT NULL PRIMARY KEY,
    event_type  VARCHAR(64)  NOT NULL,
    received_at DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE settlements (
    id                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id      BIGINT       NOT NULL,
    amount_paise         BIGINT       NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    razorpay_transfer_id VARCHAR(64)  NULL,
    payout_reference     VARCHAR(100) NULL,
    created_at           DATETIME(6)  NOT NULL,
    paid_at              DATETIME(6)  NULL,
    CONSTRAINT chk_settlements_status CHECK (status IN ('PENDING_MANUAL', 'TRANSFERRED', 'PAID')),
    INDEX idx_settlements_org (organization_id, created_at),
    CONSTRAINT fk_settlements_org FOREIGN KEY (organization_id) REFERENCES organizations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per paid booking: what the customer paid, BookEasy's fee and what the business is owed.
CREATE TABLE commission_records (
    id               BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    appointment_id   BIGINT      NOT NULL,
    organization_id  BIGINT      NOT NULL,
    gross_paise      BIGINT      NOT NULL,
    commission_paise BIGINT      NOT NULL,
    payout_paise     BIGINT      NOT NULL,
    refunded_paise   BIGINT      NOT NULL DEFAULT 0,
    settlement_id    BIGINT      NULL,
    created_at       DATETIME(6) NOT NULL,
    updated_at       DATETIME(6) NOT NULL,
    CONSTRAINT uk_commission_appointment UNIQUE (appointment_id),
    CONSTRAINT chk_commission_amounts CHECK (gross_paise >= 0 AND commission_paise >= 0 AND payout_paise >= 0),
    INDEX idx_commission_org_unsettled (organization_id, settlement_id),
    CONSTRAINT fk_commission_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id),
    CONSTRAINT fk_commission_org FOREIGN KEY (organization_id) REFERENCES organizations (id),
    CONSTRAINT fk_commission_settlement FOREIGN KEY (settlement_id) REFERENCES settlements (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Razorpay Route linked account of a business (set by a Super Admin after KYC on Razorpay).
ALTER TABLE organizations ADD COLUMN razorpay_account_id VARCHAR(64) NULL;
