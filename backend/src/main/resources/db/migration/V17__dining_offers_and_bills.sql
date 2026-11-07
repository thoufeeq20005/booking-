-- Dining: cover charge per guest (credited against the final bill), restaurant offers, and bill payments.
-- BookEasy takes no platform fee on table bookings or bill payments.

INSERT INTO platform_settings (setting_key, setting_value, value_type, description, is_public) VALUES
    ('dining.cover_per_guest.cafe',    '10.00', 'DECIMAL', 'Cover charge per guest when booking a table at a café (credited against the bill)', TRUE),
    ('dining.cover_per_guest.premium', '25.00', 'DECIMAL', 'Cover charge per guest at restro-bars, premium cafés and restaurants (credited against the bill)', TRUE);

-- CAFE: normal café or eatery; PREMIUM: restro-bar, high-end café or restaurant.
ALTER TABLE venue_profiles
    ADD COLUMN dining_tier VARCHAR(8) NOT NULL DEFAULT 'CAFE',
    ADD CONSTRAINT chk_venue_dining_tier CHECK (dining_tier IN ('CAFE', 'PREMIUM'));

-- Discounts on the final bill. meal: LUNCH (before 4 pm), DINNER (from 4 pm) or ALL_DAY.
CREATE TABLE dining_offers (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT      NOT NULL,
    title           VARCHAR(80) NOT NULL,
    percent         INT         NOT NULL,
    meal            VARCHAR(8)  NOT NULL DEFAULT 'ALL_DAY',
    created_at      DATETIME(6) NOT NULL,
    CONSTRAINT chk_dining_offers_percent CHECK (percent BETWEEN 1 AND 60),
    CONSTRAINT chk_dining_offers_meal CHECK (meal IN ('LUNCH', 'DINNER', 'ALL_DAY')),
    INDEX idx_dining_offers_org (organization_id),
    CONSTRAINT fk_dining_offers_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Table bookings keep the chosen offer. A bill payment is its own booking row: service_price is what the customer
-- pays now; bill_amount, discount_amount and cover_credit explain how it was worked out.
ALTER TABLE appointments
    ADD COLUMN offer_title             VARCHAR(80)    NULL,
    ADD COLUMN offer_percent           INT            NULL,
    ADD COLUMN bill_amount             DECIMAL(12, 2) NULL,
    ADD COLUMN discount_amount         DECIMAL(12, 2) NULL,
    ADD COLUMN cover_credit            DECIMAL(12, 2) NULL,
    ADD COLUMN bill_for_appointment_id BIGINT         NULL,
    ADD CONSTRAINT chk_appointments_offer CHECK (offer_percent IS NULL OR offer_percent BETWEEN 1 AND 60),
    ADD CONSTRAINT fk_appointments_bill_for FOREIGN KEY (bill_for_appointment_id) REFERENCES appointments (id) ON DELETE SET NULL;
