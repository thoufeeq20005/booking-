-- Offers any business can publish ("Pay for 6 months, get 6 months free", "Free health check-up this Sunday",
-- "20% off on weekdays"). Shown on the business page and used for nearby recommendations.
CREATE TABLE promotions (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    title           VARCHAR(100) NOT NULL,
    details         VARCHAR(300) NULL,
    -- DISCOUNT: percent off; FREE: something free (check-up, trial); PACKAGE: bundle or membership deal; EVENT: camp, workshop.
    kind            VARCHAR(10)  NOT NULL,
    percent_off     INT          NULL,
    valid_from      DATE         NULL,
    valid_until     DATE         NULL,
    created_at      DATETIME(6)  NOT NULL,
    CONSTRAINT chk_promotions_kind CHECK (kind IN ('DISCOUNT', 'FREE', 'PACKAGE', 'EVENT')),
    CONSTRAINT chk_promotions_percent CHECK (percent_off IS NULL OR percent_off BETWEEN 1 AND 90),
    CONSTRAINT chk_promotions_dates CHECK (valid_from IS NULL OR valid_until IS NULL OR valid_from <= valid_until),
    INDEX idx_promotions_org (organization_id),
    CONSTRAINT fk_promotions_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
