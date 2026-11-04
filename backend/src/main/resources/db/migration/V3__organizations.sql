-- BookEasy Phase 3: categories, files, organizations, branches and verification.
-- Translatable labels are JSON objects keyed by language code, e.g. {"en": "Dental clinic", "ta": "..."};
-- "en" is always present and is the fallback.

-- Metadata for every stored file. The bytes live in FileStorage under `storage_key`, never under a
-- user-supplied name. PRIVATE files are only streamed through endpoints that check ownership.
CREATE TABLE uploaded_files (
    id                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    public_id         CHAR(36)     NOT NULL,
    storage_key       VARCHAR(191) NOT NULL,
    original_name     VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    sha256            CHAR(64)     NOT NULL,
    visibility        VARCHAR(16)  NOT NULL,
    purpose           VARCHAR(32)  NOT NULL,
    owner_user_id     BIGINT       NULL,
    created_at        DATETIME(6)  NOT NULL,
    CONSTRAINT uk_uploaded_files_public_id UNIQUE (public_id),
    CONSTRAINT uk_uploaded_files_storage_key UNIQUE (storage_key),
    CONSTRAINT chk_uploaded_files_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT chk_uploaded_files_size CHECK (size_bytes > 0),
    INDEX idx_uploaded_files_owner (owner_user_id),
    CONSTRAINT fk_uploaded_files_owner FOREIGN KEY (owner_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE categories (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    slug             VARCHAR(64)  NOT NULL,
    name_i18n        JSON         NOT NULL,
    description_i18n JSON         NULL,
    -- Name of a Lucide icon shown in the UI (e.g. "stethoscope").
    icon             VARCHAR(48)  NOT NULL DEFAULT 'store',
    booking_workflow VARCHAR(16)  NOT NULL DEFAULT 'GENERIC',
    filter_schema    JSON         NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order       INT          NOT NULL DEFAULT 0,
    created_at       DATETIME(6)  NOT NULL,
    updated_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uk_categories_slug UNIQUE (slug),
    CONSTRAINT chk_categories_workflow CHECK (booking_workflow IN ('HEALTHCARE', 'SALON', 'EDUCATION', 'GENERIC')),
    INDEX idx_categories_active (is_active, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE subcategories (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id BIGINT      NOT NULL,
    slug        VARCHAR(64) NOT NULL,
    name_i18n   JSON        NOT NULL,
    is_active   BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME(6) NOT NULL,
    updated_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_subcategories_slug UNIQUE (category_id, slug),
    CONSTRAINT fk_subcategories_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Documents a business must upload to be verified in a category.
CREATE TABLE category_document_requirements (
    id                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    category_id        BIGINT       NOT NULL,
    code               VARCHAR(48)  NOT NULL,
    label_i18n         JSON         NOT NULL,
    description_i18n   JSON         NULL,
    is_mandatory       BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Comma-separated subset of: application/pdf,image/jpeg,image/png,image/webp
    allowed_mime_types VARCHAR(255) NOT NULL,
    max_size_bytes     INT          NOT NULL,
    is_active          BOOLEAN      NOT NULL DEFAULT TRUE,
    sort_order         INT          NOT NULL DEFAULT 0,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    CONSTRAINT uk_doc_requirements_code UNIQUE (category_id, code),
    CONSTRAINT chk_doc_requirements_size CHECK (max_size_bytes > 0),
    CONSTRAINT fk_doc_requirements_category FOREIGN KEY (category_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE organizations (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    subcategory_id      BIGINT        NOT NULL,
    name                VARCHAR(150)  NOT NULL,
    slug                VARCHAR(160)  NOT NULL,
    description         VARCHAR(2000) NULL,
    logo_file_id        BIGINT        NULL,
    contact_email       VARCHAR(255)  NULL,
    contact_phone       VARCHAR(16)   NULL,
    website             VARCHAR(255)  NULL,
    gstin               CHAR(15)      NULL,
    verification_status VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    -- Latest reviewer reason (rejection or suspension), shown to the owner.
    status_reason       VARCHAR(1000) NULL,
    confirmation_mode   VARCHAR(16)   NOT NULL DEFAULT 'INSTANT',
    rating_avg          DECIMAL(3, 2) NOT NULL DEFAULT 0.00,
    review_count        INT           NOT NULL DEFAULT 0,
    submitted_at        DATETIME(6)   NULL,
    approved_at         DATETIME(6)   NULL,
    created_by          BIGINT        NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uk_organizations_slug UNIQUE (slug),
    CONSTRAINT chk_organizations_status CHECK (verification_status IN ('DRAFT', 'PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT chk_organizations_confirmation CHECK (confirmation_mode IN ('INSTANT', 'APPROVAL')),
    CONSTRAINT chk_organizations_rating CHECK (rating_avg BETWEEN 0 AND 5),
    INDEX idx_organizations_status (verification_status, submitted_at),
    INDEX idx_organizations_subcategory (subcategory_id, verification_status),
    CONSTRAINT fk_organizations_subcategory FOREIGN KEY (subcategory_id) REFERENCES subcategories (id),
    CONSTRAINT fk_organizations_logo FOREIGN KEY (logo_file_id) REFERENCES uploaded_files (id) ON DELETE SET NULL,
    CONSTRAINT fk_organizations_created_by FOREIGN KEY (created_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE organization_owners (
    organization_id BIGINT      NOT NULL,
    user_id         BIGINT      NOT NULL,
    role            VARCHAR(16) NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (organization_id, user_id),
    CONSTRAINT chk_organization_owners_role CHECK (role IN ('PRIMARY', 'CO_OWNER')),
    INDEX idx_organization_owners_user (user_id),
    CONSTRAINT fk_organization_owners_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_owners_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE organization_branches (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT       NOT NULL,
    name            VARCHAR(150) NOT NULL,
    address_line1   VARCHAR(200) NOT NULL,
    address_line2   VARCHAR(200) NULL,
    locality        VARCHAR(120) NULL,
    city            VARCHAR(120) NOT NULL,
    state           VARCHAR(120) NOT NULL,
    pincode         CHAR(6)      NOT NULL,
    latitude        DECIMAL(9, 6) NOT NULL,
    longitude       DECIMAL(9, 6) NOT NULL,
    -- Derived from latitude/longitude so it can never disagree with them. For SRID 4326 MySQL reads
    -- POINT(x, y) as (longitude, latitude). Used by nearby search (Phase 6).
    location        POINT GENERATED ALWAYS AS (ST_SRID(POINT(longitude, latitude), 4326)) STORED NOT NULL SRID 4326,
    phone           VARCHAR(16)  NULL,
    timezone        VARCHAR(64)  NOT NULL DEFAULT 'Asia/Kolkata',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    CONSTRAINT chk_branches_pincode CHECK (pincode REGEXP '^[1-9][0-9]{5}$'),
    CONSTRAINT chk_branches_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_branches_longitude CHECK (longitude BETWEEN -180 AND 180),
    INDEX idx_branches_org (organization_id),
    INDEX idx_branches_city (city, is_active),
    INDEX idx_branches_pincode (pincode),
    SPATIAL INDEX sidx_branches_location (location),
    CONSTRAINT fk_branches_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Weekly opening hours in the branch's timezone. No row for a day = closed. Overnight hours are not supported.
CREATE TABLE working_hours (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    branch_id   BIGINT      NOT NULL,
    day_of_week TINYINT     NOT NULL,
    opens_at    TIME        NOT NULL,
    closes_at   TIME        NOT NULL,
    break_start TIME        NULL,
    break_end   TIME        NULL,
    CONSTRAINT uk_working_hours_day UNIQUE (branch_id, day_of_week),
    CONSTRAINT chk_working_hours_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT chk_working_hours_order CHECK (opens_at < closes_at),
    CONSTRAINT chk_working_hours_break CHECK (
        (break_start IS NULL AND break_end IS NULL)
        OR (break_start IS NOT NULL AND break_end IS NOT NULL AND opens_at < break_start
            AND break_start < break_end AND break_end < closes_at)),
    CONSTRAINT fk_working_hours_branch FOREIGN KEY (branch_id) REFERENCES organization_branches (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE holidays (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    branch_id    BIGINT       NOT NULL,
    holiday_date DATE         NOT NULL,
    reason       VARCHAR(200) NULL,
    created_at   DATETIME(6)  NOT NULL,
    CONSTRAINT uk_holidays_branch_date UNIQUE (branch_id, holiday_date),
    CONSTRAINT fk_holidays_branch FOREIGN KEY (branch_id) REFERENCES organization_branches (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- The current document for each requirement. Re-uploading replaces the file and resets the review.
CREATE TABLE organization_documents (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT        NOT NULL,
    requirement_id  BIGINT        NOT NULL,
    file_id         BIGINT        NOT NULL,
    status          VARCHAR(16)   NOT NULL DEFAULT 'SUBMITTED',
    reviewer_note   VARCHAR(1000) NULL,
    reviewed_by     BIGINT        NULL,
    reviewed_at     DATETIME(6)   NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    CONSTRAINT uk_organization_documents_req UNIQUE (organization_id, requirement_id),
    CONSTRAINT uk_organization_documents_file UNIQUE (file_id),
    CONSTRAINT chk_organization_documents_status CHECK (status IN ('SUBMITTED', 'ACCEPTED', 'REJECTED')),
    CONSTRAINT fk_organization_documents_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_documents_req FOREIGN KEY (requirement_id) REFERENCES category_document_requirements (id),
    CONSTRAINT fk_organization_documents_file FOREIGN KEY (file_id) REFERENCES uploaded_files (id),
    CONSTRAINT fk_organization_documents_reviewer FOREIGN KEY (reviewed_by) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- Every verification decision and status change, newest last. Complements audit_logs with an owner-visible history.
CREATE TABLE organization_verifications (
    id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    organization_id BIGINT        NOT NULL,
    action          VARCHAR(16)   NOT NULL,
    from_status     VARCHAR(16)   NOT NULL,
    to_status       VARCHAR(16)   NOT NULL,
    reason          VARCHAR(1000) NULL,
    actor_user_id   BIGINT        NULL,
    created_at      DATETIME(6)   NOT NULL,
    CONSTRAINT chk_organization_verifications_action CHECK (action IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'SUSPENDED', 'REINSTATED')),
    INDEX idx_organization_verifications_org (organization_id, created_at),
    CONSTRAINT fk_organization_verifications_org FOREIGN KEY (organization_id) REFERENCES organizations (id) ON DELETE CASCADE,
    CONSTRAINT fk_organization_verifications_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------------------------------------
-- Starter catalogue. Super Admins can edit, deactivate or extend all of it from the admin console.
-- ---------------------------------------------------------------------------------------------------------
INSERT INTO categories (id, slug, name_i18n, description_i18n, icon, booking_workflow, sort_order, created_at, updated_at) VALUES
 (1, 'healthcare',
  '{"en":"Healthcare","ta":"மருத்துவம்","hi":"स्वास्थ्य सेवा","ml":"ആരോഗ്യ സേവനം","te":"ఆరోగ్య సేవలు","kn":"ಆರೋಗ್ಯ ಸೇವೆ"}',
  '{"en":"Clinics, hospitals and diagnostic centres","ta":"கிளினிக்குகள், மருத்துவமனைகள் மற்றும் பரிசோதனை மையங்கள்","hi":"क्लिनिक, अस्पताल और जाँच केंद्र","ml":"ക്ലിനിക്കുകൾ, ആശുപത്രികൾ, പരിശോധനാ കേന്ദ്രങ്ങൾ","te":"క్లినిక్‌లు, ఆసుపత్రులు, పరీక్షా కేంద్రాలు","kn":"ಕ್ಲಿನಿಕ್‌ಗಳು, ಆಸ್ಪತ್ರೆಗಳು ಮತ್ತು ಪರೀಕ್ಷಾ ಕೇಂದ್ರಗಳು"}',
  'stethoscope', 'HEALTHCARE', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (2, 'salon-spa',
  '{"en":"Salon & spa","ta":"அழகு நிலையம் & ஸ்பா","hi":"सैलून और स्पा","ml":"സലൂൺ & സ്പാ","te":"సెలూన్ & స్పా","kn":"ಸಲೂನ್ & ಸ್ಪಾ"}',
  '{"en":"Hair, beauty and wellness","ta":"முடி, அழகு மற்றும் நலவாழ்வு","hi":"बाल, सौंदर्य और वेलनेस","ml":"മുടി, സൗന്ദര്യം, ആരോഗ്യപരിപാലനം","te":"జుట్టు, అందం మరియు వెల్‌నెస్","kn":"ಕೂದಲು, ಸೌಂದರ್ಯ ಮತ್ತು ಆರೋಗ್ಯ"}',
  'scissors', 'SALON', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (3, 'education',
  '{"en":"Education & coaching","ta":"கல்வி & பயிற்சி","hi":"शिक्षा और कोचिंग","ml":"വിദ്യാഭ്യാസം & പരിശീലനം","te":"విద్య & కోచింగ్","kn":"ಶಿಕ್ಷಣ & ತರಬೇತಿ"}',
  '{"en":"Tuition, coaching and classes","ta":"டியூஷன், பயிற்சி மற்றும் வகுப்புகள்","hi":"ट्यूशन, कोचिंग और कक्षाएँ","ml":"ട്യൂഷൻ, പരിശീലനം, ക്ലാസുകൾ","te":"ట్యూషన్, కోచింగ్ మరియు తరగతులు","kn":"ಟ್ಯೂಷನ್, ತರಬೇತಿ ಮತ್ತು ತರಗತಿಗಳು"}',
  'graduation-cap', 'EDUCATION', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (4, 'fitness',
  '{"en":"Fitness","ta":"உடற்பயிற்சி","hi":"फिटनेस","ml":"ഫിറ്റ്നസ്","te":"ఫిట్‌నెస్","kn":"ಫಿಟ್‌ನೆಸ್"}',
  '{"en":"Gyms, yoga and personal training","ta":"ஜிம், யோகா மற்றும் தனிப்பட்ட பயிற்சி","hi":"जिम, योग और पर्सनल ट्रेनिंग","ml":"ജിം, യോഗ, വ്യക്തിഗത പരിശീലനം","te":"జిమ్, యోగా మరియు వ్యక్తిగత శిక్షణ","kn":"ಜಿಮ್, ಯೋಗ ಮತ್ತು ವೈಯಕ್ತಿಕ ತರಬೇತಿ"}',
  'dumbbell', 'GENERIC', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

INSERT INTO subcategories (category_id, slug, name_i18n, sort_order, created_at, updated_at) VALUES
 (1, 'general-clinic', '{"en":"General clinic","ta":"பொது கிளினிக்","hi":"सामान्य क्लिनिक","ml":"ജനറൽ ക്ലിനിക്","te":"జనరల్ క్లినిక్","kn":"ಸಾಮಾನ್ಯ ಕ್ಲಿನಿಕ್"}', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (1, 'dental-clinic', '{"en":"Dental clinic","ta":"பல் மருத்துவமனை","hi":"दंत चिकित्सालय","ml":"ദന്തൽ ക്ലിനിക്","te":"దంత వైద్యశాల","kn":"ದಂತ ಚಿಕಿತ್ಸಾಲಯ"}', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (1, 'eye-care', '{"en":"Eye care","ta":"கண் பராமரிப்பு","hi":"नेत्र चिकित्सा","ml":"നേത്ര പരിചരണം","te":"కంటి సంరక్షణ","kn":"ಕಣ್ಣಿನ ಆರೈಕೆ"}', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (1, 'diagnostics', '{"en":"Diagnostic centre","ta":"பரிசோதனை மையம்","hi":"जाँच केंद्र","ml":"പരിശോധനാ കേന്ദ്രം","te":"పరీక్షా కేంద్రం","kn":"ಪರೀಕ್ಷಾ ಕೇಂದ್ರ"}', 40, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (2, 'unisex-salon', '{"en":"Unisex salon","ta":"யூனிசெக்ஸ் சலூன்","hi":"यूनिसेक्स सैलून","ml":"യൂണിസെക്സ് സലൂൺ","te":"యూనిసెక్స్ సెలూన్","kn":"ಯುನಿಸೆಕ್ಸ್ ಸಲೂನ್"}', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (2, 'beauty-parlour', '{"en":"Beauty parlour","ta":"அழகு நிலையம்","hi":"ब्यूटी पार्लर","ml":"ബ്യൂട്ടി പാർലർ","te":"బ్యూటీ పార్లర్","kn":"ಬ್ಯೂಟಿ ಪಾರ್ಲರ್"}', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (2, 'spa', '{"en":"Spa & massage","ta":"ஸ்பா & மசாஜ்","hi":"स्पा और मसाज","ml":"സ്പാ & മസാജ്","te":"స్పా & మసాజ్","kn":"ಸ್ಪಾ & ಮಸಾಜ್"}', 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (3, 'tuition-centre', '{"en":"Tuition centre","ta":"டியூஷன் மையம்","hi":"ट्यूशन सेंटर","ml":"ട്യൂഷൻ സെന്റർ","te":"ట్యూషన్ కేంద్రం","kn":"ಟ್ಯೂಷನ್ ಕೇಂದ್ರ"}', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (3, 'music-dance', '{"en":"Music & dance","ta":"இசை & நடனம்","hi":"संगीत और नृत्य","ml":"സംഗീതം & നൃത്തം","te":"సంగీతం & నృత్యం","kn":"ಸಂಗೀತ & ನೃತ್ಯ"}', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (4, 'gym', '{"en":"Gym","ta":"ஜிம்","hi":"जिम","ml":"ജിം","te":"జిమ్","kn":"ಜಿಮ್"}', 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (4, 'yoga-studio', '{"en":"Yoga studio","ta":"யோகா மையம்","hi":"योग स्टूडियो","ml":"യോഗ സ്റ്റുഡിയോ","te":"యోగా స్టూడియో","kn":"ಯೋಗ ಸ್ಟುಡಿಯೋ"}', 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));

-- Every category asks for owner ID proof; regulated categories add their licences.
INSERT INTO category_document_requirements (category_id, code, label_i18n, is_mandatory, allowed_mime_types, max_size_bytes, sort_order, created_at, updated_at)
SELECT c.id, 'OWNER_ID_PROOF',
  '{"en":"Owner ID proof (Aadhaar, PAN or passport)","ta":"உரிமையாளர் அடையாளச் சான்று (ஆதார், பான் அல்லது கடவுச்சீட்டு)","hi":"मालिक का पहचान प्रमाण (आधार, पैन या पासपोर्ट)","ml":"ഉടമയുടെ തിരിച്ചറിയൽ രേഖ (ആധാർ, പാൻ അല്ലെങ്കിൽ പാസ്‌പോർട്ട്)","te":"యజమాని గుర్తింపు పత్రం (ఆధార్, పాన్ లేదా పాస్‌పోర్ట్)","kn":"ಮಾಲೀಕರ ಗುರುತಿನ ಪುರಾವೆ (ಆಧಾರ್, ಪ್ಯಾನ್ ಅಥವಾ ಪಾಸ್‌ಪೋರ್ಟ್)"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 5242880, 10, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
FROM categories c;

INSERT INTO category_document_requirements (category_id, code, label_i18n, is_mandatory, allowed_mime_types, max_size_bytes, sort_order, created_at, updated_at) VALUES
 (1, 'CLINIC_REGISTRATION',
  '{"en":"Clinical establishment registration","ta":"மருத்துவ நிறுவனப் பதிவுச் சான்று","hi":"क्लिनिकल प्रतिष्ठान पंजीकरण","ml":"ക്ലിനിക്കൽ സ്ഥാപന രജിസ്ട്രേഷൻ","te":"క్లినికల్ సంస్థ నమోదు","kn":"ಕ್ಲಿನಿಕಲ್ ಸಂಸ್ಥೆ ನೋಂದಣಿ"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 10485760, 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (1, 'MEDICAL_REGISTRATION',
  '{"en":"Doctor''s medical council registration","ta":"மருத்துவரின் மருத்துவக் கவுன்சில் பதிவு","hi":"डॉक्टर का मेडिकल काउंसिल पंजीकरण","ml":"ഡോക്ടറുടെ മെഡിക്കൽ കൗൺസിൽ രജിസ്ട്രേഷൻ","te":"వైద్యుని మెడికల్ కౌన్సిల్ నమోదు","kn":"ವೈದ್ಯರ ವೈದ್ಯಕೀಯ ಮಂಡಳಿ ನೋಂದಣಿ"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 10485760, 30, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (2, 'TRADE_LICENCE',
  '{"en":"Trade licence / shop establishment certificate","ta":"வர்த்தக உரிமம் / கடை நிறுவனச் சான்று","hi":"ट्रेड लाइसेंस / दुकान स्थापना प्रमाणपत्र","ml":"ട്രേഡ് ലൈസൻസ് / ഷോപ്പ് എസ്റ്റാബ്ലിഷ്മെന്റ് സർട്ടിഫിക്കറ്റ്","te":"ట్రేడ్ లైసెన్స్ / షాప్ ఎస్టాబ్లిష్‌మెంట్ సర్టిఫికేట్","kn":"ವ್ಯಾಪಾರ ಪರವಾನಗಿ / ಅಂಗಡಿ ಸ್ಥಾಪನೆ ಪ್ರಮಾಣಪತ್ರ"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 10485760, 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (3, 'INSTITUTE_REGISTRATION',
  '{"en":"Institute registration or trade licence","ta":"நிறுவனப் பதிவு அல்லது வர்த்தக உரிமம்","hi":"संस्थान पंजीकरण या ट्रेड लाइसेंस","ml":"സ്ഥാപന രജിസ്ട്രേഷൻ അല്ലെങ്കിൽ ട്രേഡ് ലൈസൻസ്","te":"సంస్థ నమోదు లేదా ట్రేడ్ లైసెన్స్","kn":"ಸಂಸ್ಥೆ ನೋಂದಣಿ ಅಥವಾ ವ್ಯಾಪಾರ ಪರವಾನಗಿ"}',
  FALSE, 'application/pdf,image/jpeg,image/png', 10485760, 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)),
 (4, 'TRADE_LICENCE',
  '{"en":"Trade licence / shop establishment certificate","ta":"வர்த்தக உரிமம் / கடை நிறுவனச் சான்று","hi":"ट्रेड लाइसेंस / दुकान स्थापना प्रमाणपत्र","ml":"ട്രേഡ് ലൈസൻസ് / ഷോപ്പ് എസ്റ്റാബ്ലിഷ്മെന്റ് സർട്ടിഫിക്കറ്റ്","te":"ట్రేడ్ లైసెన్స్ / షాప్ ఎస్టాబ్లిష్‌మెంట్ సర్టిఫికేట్","kn":"ವ್ಯಾಪಾರ ಪರವಾನಗಿ / ಅಂಗಡಿ ಸ್ಥಾಪನೆ ಪ್ರಮಾಣಪತ್ರ"}',
  TRUE, 'application/pdf,image/jpeg,image/png', 10485760, 20, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6));
