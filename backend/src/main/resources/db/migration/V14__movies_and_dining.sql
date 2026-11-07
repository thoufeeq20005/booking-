-- BookEasy: movie tickets (theatres, screens, showtimes, seats) and dining (restaurants/cafés, menus, table bookings).
-- Both reuse organizations/branches for the venue and appointments for the customer's booking, so payment, QR pass,
-- receipts, cancellation and notifications work unchanged.

ALTER TABLE categories
    DROP CHECK chk_categories_workflow,
    ADD CONSTRAINT chk_categories_workflow CHECK (booking_workflow IN ('HEALTHCARE', 'SALON', 'EDUCATION', 'GENERIC', 'CINEMA', 'DINING'));

INSERT INTO categories (id, slug, name_i18n, description_i18n, icon, booking_workflow, sort_order, created_at, updated_at) VALUES
 (5, 'movies',
  '{"en":"Movies","ta":"திரைப்படங்கள்","hi":"फ़िल्में","ml":"സിനിമകൾ","te":"సినిమాలు","kn":"ಚಲನಚಿತ್ರಗಳು"}',
  '{"en":"Cinemas and movie tickets","ta":"திரையரங்குகள் மற்றும் திரைப்பட டிக்கெட்டுகள்","hi":"सिनेमाघर और फ़िल्म टिकट","ml":"തിയേറ്ററുകളും സിനിമാ ടിക്കറ്റുകളും","te":"సినిమా హాళ్లు మరియు టికెట్లు","kn":"ಚಿತ್ರಮಂದಿರಗಳು ಮತ್ತು ಟಿಕೆಟ್‌ಗಳು"}',
  'clapperboard', 'CINEMA', 50, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (6, 'dining',
  '{"en":"Dining","ta":"உணவகங்கள்","hi":"डाइनिंग","ml":"ഡൈനിംഗ്","te":"డైనింగ్","kn":"ಡೈನಿಂಗ್"}',
  '{"en":"Restaurants and cafés – book a table","ta":"உணவகங்கள் மற்றும் கஃபேக்கள் – மேசையை முன்பதிவு செய்யுங்கள்","hi":"रेस्टोरेंट और कैफ़े – टेबल बुक करें","ml":"റെസ്റ്റോറന്റുകളും കഫേകളും – ടേബിൾ ബുക്ക് ചെയ്യുക","te":"రెస్టారెంట్లు మరియు కేఫ్‌లు – టేబుల్ బుక్ చేయండి","kn":"ರೆಸ್ಟೋರೆಂಟ್‌ಗಳು ಮತ್ತು ಕೆಫೆಗಳು – ಟೇಬಲ್ ಬುಕ್ ಮಾಡಿ"}',
  'utensils', 'DINING', 60, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO subcategories (id, category_id, slug, name_i18n, sort_order, created_at, updated_at) VALUES
 (12, 5, 'cinema', '{"en":"Cinema","ta":"திரையரங்கு","hi":"सिनेमाघर","ml":"തിയേറ്റർ","te":"సినిమా హాల్","kn":"ಚಿತ್ರಮಂದಿರ"}', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (13, 6, 'restaurant', '{"en":"Restaurant","ta":"உணவகம்","hi":"रेस्टोरेंट","ml":"റെസ്റ്റോറന്റ്","te":"రెస్టారెంట్","kn":"ರೆಸ್ಟೋರೆಂಟ್"}', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (14, 6, 'cafe', '{"en":"Café","ta":"கஃபே","hi":"कैफ़े","ml":"കഫേ","te":"కేఫ్","kn":"ಕೆಫೆ"}', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO category_document_requirements (category_id, code, label_i18n, is_mandatory, allowed_mime_types, max_size_bytes, sort_order, created_at, updated_at) VALUES
 (5, 'OWNER_ID_PROOF',
  '{"en":"Owner ID proof (Aadhaar, PAN or passport)","ta":"உரிமையாளர் அடையாளச் சான்று (ஆதார், பான் அல்லது கடவுச்சீட்டு)","hi":"मालिक का पहचान प्रमाण (आधार, पैन या पासपोर्ट)","ml":"ഉടമയുടെ തിരിച്ചറിയൽ രേഖ (ആധാർ, പാൻ അല്ലെങ്കിൽ പാസ്‌പോർട്ട്)","te":"యజమాని గుర్తింపు పత్రం (ఆధార్, పాన్ లేదా పాస్‌పోర్ట్)","kn":"ಮಾಲೀಕರ ಗುರುತಿನ ಪುರಾವೆ (ಆಧಾರ್, ಪ್ಯಾನ್ ಅಥವಾ ಪಾಸ್‌ಪೋರ್ಟ್)"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 5242880, 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (5, 'CINEMA_LICENCE',
  '{"en":"Cinema licence","ta":"திரையரங்க உரிமம்","hi":"सिनेमा लाइसेंस","ml":"സിനിമാ ലൈസൻസ്","te":"సినిమా లైసెన్స్","kn":"ಚಿತ್ರಮಂದಿರ ಪರವಾನಗಿ"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 10485760, 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (6, 'OWNER_ID_PROOF',
  '{"en":"Owner ID proof (Aadhaar, PAN or passport)","ta":"உரிமையாளர் அடையாளச் சான்று (ஆதார், பான் அல்லது கடவுச்சீட்டு)","hi":"मालिक का पहचान प्रमाण (आधार, पैन या पासपोर्ट)","ml":"ഉടമയുടെ തിരിച്ചറിയൽ രേഖ (ആധാർ, പാൻ അല്ലെങ്കിൽ പാസ്‌പോർട്ട്)","te":"యజమాని గుర్తింపు పత్రం (ఆధార్, పాన్ లేదా పాస్‌పోర్ట్)","kn":"ಮಾಲೀಕರ ಗುರುತಿನ ಪುರಾವೆ (ಆಧಾರ್, ಪ್ಯಾನ್ ಅಥವಾ ಪಾಸ್‌ಪೋರ್ಟ್)"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 5242880, 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (6, 'FSSAI_LICENCE',
  '{"en":"FSSAI food licence","ta":"FSSAI உணவு உரிமம்","hi":"FSSAI खाद्य लाइसेंस","ml":"FSSAI ഭക്ഷ്യ ലൈസൻസ്","te":"FSSAI ఆహార లైసెన్స్","kn":"FSSAI ಆಹಾರ ಪರವಾನಗಿ"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 10485760, 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- Venue settings for cinema and dining businesses. booking_service_id is a hidden (inactive) service that
-- customer bookings point at, so they fit the existing appointments table.
CREATE TABLE venue_profiles (
    organization_id    BIGINT        NOT NULL PRIMARY KEY,
    kind               VARCHAR(8)    NOT NULL,
    booking_service_id BIGINT        NOT NULL,
    cuisines           VARCHAR(200)  NULL,
    cost_for_two       DECIMAL(10, 2) NULL,
    veg_only           BOOLEAN       NOT NULL DEFAULT FALSE,
    -- Dining: guests that can be seated per 90-minute sitting window.
    capacity_per_slot  INT           NULL,
    offer_text         VARCHAR(200)  NULL,
    features           VARCHAR(300)  NULL,
    CONSTRAINT chk_venue_kind CHECK (kind IN ('CINEMA', 'DINING')),
    CONSTRAINT chk_venue_capacity CHECK (capacity_per_slot IS NULL OR capacity_per_slot BETWEEN 1 AND 2000),
    CONSTRAINT fk_venue_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_venue_service FOREIGN KEY (booking_service_id) REFERENCES services (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE menu_items (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT        NOT NULL,
    section         VARCHAR(60)   NOT NULL,
    name            VARCHAR(120)  NOT NULL,
    description     VARCHAR(300)  NULL,
    price           DECIMAL(10, 2) NOT NULL,
    is_veg          BOOLEAN       NOT NULL DEFAULT TRUE,
    is_bestseller   BOOLEAN       NOT NULL DEFAULT FALSE,
    sort_order      INT           NOT NULL DEFAULT 0,
    CONSTRAINT chk_menu_price CHECK (price >= 0),
    INDEX idx_menu_org (organization_id, sort_order),
    CONSTRAINT fk_menu_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE movies (
    id               BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    title            VARCHAR(150)  NOT NULL,
    languages        VARCHAR(120)  NOT NULL,
    certificate      VARCHAR(4)    NOT NULL,
    genres           VARCHAR(120)  NOT NULL,
    duration_minutes INT           NOT NULL,
    synopsis         VARCHAR(1000) NULL,
    cast_names       VARCHAR(300)  NULL,
    release_date     DATE          NOT NULL,
    -- Two hex colours for the generated poster artwork (no copyrighted posters are stored).
    poster_from      CHAR(7)       NOT NULL DEFAULT '#1d4ed8',
    poster_to        CHAR(7)       NOT NULL DEFAULT '#0f172a',
    is_active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)   NOT NULL,
    CONSTRAINT chk_movies_certificate CHECK (certificate IN ('U', 'UA', 'A')),
    CONSTRAINT chk_movies_duration CHECK (duration_minutes BETWEEN 30 AND 300)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- layout: [{"row":"A","seats":14,"tier":"CLASSIC","gapsAfter":[4,10]}, ...] listed from the front row to the back.
CREATE TABLE cinema_screens (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    branch_id  BIGINT      NOT NULL,
    name       VARCHAR(40) NOT NULL,
    layout     JSON        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_screens_branch (branch_id),
    CONSTRAINT fk_screens_branch FOREIGN KEY (branch_id) REFERENCES organization_branches (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- prices: {"CLASSIC":180.00,"PRIME":250.00,"RECLINER":450.00}
CREATE TABLE showtimes (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    screen_id  BIGINT      NOT NULL,
    movie_id   BIGINT      NOT NULL,
    starts_at  DATETIME(6) NOT NULL,
    format     VARCHAR(8)  NOT NULL,
    language   VARCHAR(40) NOT NULL,
    prices     JSON        NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_showtimes_format CHECK (format IN ('2D', '3D', 'IMAX', '4DX')),
    INDEX idx_showtimes_movie (movie_id, starts_at),
    INDEX idx_showtimes_screen (screen_id, starts_at),
    CONSTRAINT fk_showtimes_screen FOREIGN KEY (screen_id) REFERENCES cinema_screens (id) ON DELETE CASCADE,
    CONSTRAINT fk_showtimes_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- One row per sold or held seat. The primary key makes selling the same seat twice impossible; rows are deleted
-- when the booking is cancelled, declined or its payment hold expires.
CREATE TABLE ticket_seats (
    showtime_id    BIGINT        NOT NULL,
    seat_label     VARCHAR(8)    NOT NULL,
    appointment_id BIGINT        NOT NULL,
    tier           VARCHAR(16)   NOT NULL,
    price          DECIMAL(10, 2) NOT NULL,
    PRIMARY KEY (showtime_id, seat_label),
    INDEX idx_ticket_seats_appointment (appointment_id),
    CONSTRAINT fk_ticket_seats_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes (id) ON DELETE CASCADE,
    CONSTRAINT fk_ticket_seats_appointment FOREIGN KEY (appointment_id) REFERENCES appointments (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

ALTER TABLE appointments
    ADD COLUMN showtime_id BIGINT NULL,
    ADD COLUMN party_size  INT    NULL,
    ADD CONSTRAINT chk_appointments_party CHECK (party_size IS NULL OR party_size BETWEEN 1 AND 50),
    ADD CONSTRAINT fk_appointments_showtime FOREIGN KEY (showtime_id) REFERENCES showtimes (id) ON DELETE SET NULL;
