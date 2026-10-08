-- =====================================================================
-- V3 - Public website content, enquiries and file storage.
--
-- Creates: manpower_categories, service_offerings, industries, testimonials,
--          enquiries, contact_messages, documents
--
-- Adds the CONTENT_MANAGE permission and grants it to SUPER_ADMIN and ADMIN.
--
-- NOTE on manpower_categories: the plan placed this in Phase 4 alongside rate
-- cards. It is created here instead because the public enquiry form needs a
-- category dropdown, and a free-text field would produce unusable data on the
-- one form the sales team actually reads. Phase 4 builds skills and rate cards
-- on top of this table rather than creating it.
-- =====================================================================

-- ---------------------------------------------------------------------
-- manpower_categories - the kinds of worker this company supplies
-- ---------------------------------------------------------------------
CREATE TABLE manpower_categories
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,

    code          VARCHAR(40)  NOT NULL COMMENT 'stable identifier, e.g. SECURITY_GUARD',
    name          VARCHAR(120) NOT NULL,
    -- Determines which minimum-wage band applies in Phase 8. Not cosmetic.
    skill_level   VARCHAR(20)  NOT NULL COMMENT 'UNSKILLED|SEMI_SKILLED|SKILLED|HIGHLY_SKILLED',
    description   VARCHAR(500) NULL,
    display_order INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(150) NULL,
    updated_by    VARCHAR(150) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_manpower_categories PRIMARY KEY (id),
    CONSTRAINT uk_manpower_categories_code UNIQUE (code),
    CONSTRAINT uk_manpower_categories_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_manpower_categories_active ON manpower_categories (active, display_order);

-- ---------------------------------------------------------------------
-- service_offerings - the Services pages of the marketing site
-- ---------------------------------------------------------------------
CREATE TABLE service_offerings
(
    id                BIGINT       NOT NULL AUTO_INCREMENT,

    -- URL segment, e.g. /services/manpower-supply. Unique and immutable once
    -- published: changing it breaks inbound links and search rankings.
    slug              VARCHAR(120) NOT NULL,
    title             VARCHAR(160) NOT NULL,
    summary           VARCHAR(400) NOT NULL COMMENT 'card text on the services index',
    description       TEXT         NULL COMMENT 'full page body',
    icon              VARCHAR(60)  NULL COMMENT 'icon name for the site to render',
    hero_image_path   VARCHAR(400) NULL,

    -- SEO. Separate from title/summary because a good page heading and a good
    -- search-result snippet are rarely the same text.
    meta_title        VARCHAR(160) NULL,
    meta_description  VARCHAR(320) NULL,

    display_order     INT          NOT NULL DEFAULT 0,
    published         BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(150) NULL,
    updated_by        VARCHAR(150) NULL,
    version           BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_service_offerings PRIMARY KEY (id),
    CONSTRAINT uk_service_offerings_slug UNIQUE (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_service_offerings_published ON service_offerings (published, display_order);

-- ---------------------------------------------------------------------
-- industries - the Industries Served page
-- ---------------------------------------------------------------------
CREATE TABLE industries
(
    id            BIGINT       NOT NULL AUTO_INCREMENT,

    slug          VARCHAR(120) NOT NULL,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(600) NULL,
    icon          VARCHAR(60)  NULL,
    display_order INT          NOT NULL DEFAULT 0,
    published     BOOLEAN      NOT NULL DEFAULT FALSE,

    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    created_by    VARCHAR(150) NULL,
    updated_by    VARCHAR(150) NULL,
    version       BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_industries PRIMARY KEY (id),
    CONSTRAINT uk_industries_slug UNIQUE (slug)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_industries_published ON industries (published, display_order);

-- ---------------------------------------------------------------------
-- testimonials - client quotes, managed from admin
-- ---------------------------------------------------------------------
CREATE TABLE testimonials
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,

    client_name    VARCHAR(120)  NOT NULL COMMENT 'the person quoted',
    client_company VARCHAR(200)  NULL,
    designation    VARCHAR(120)  NULL,
    content        VARCHAR(1500) NOT NULL,
    rating         INT           NULL COMMENT '1-5, optional',
    logo_path      VARCHAR(400)  NULL COMMENT 'company logo',
    photo_path     VARCHAR(400)  NULL COMMENT 'photo of the person',

    display_order  INT           NOT NULL DEFAULT 0,
    -- Unpublished by default: a quote must be approved before it appears on
    -- the public site, even though only staff can create one.
    published      BOOLEAN       NOT NULL DEFAULT FALSE,

    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    created_by     VARCHAR(150)  NULL,
    updated_by     VARCHAR(150)  NULL,
    version        BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_testimonials PRIMARY KEY (id),
    CONSTRAINT ck_testimonials_rating CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_testimonials_published ON testimonials (published, display_order);

-- ---------------------------------------------------------------------
-- enquiries - the Manpower Requirement form. The sales pipeline starts here.
-- ---------------------------------------------------------------------
CREATE TABLE enquiries
(
    id                  BIGINT        NOT NULL AUTO_INCREMENT,

    company_name        VARCHAR(200)  NOT NULL,
    contact_person      VARCHAR(120)  NOT NULL,
    phone               VARCHAR(20)   NOT NULL,
    email               VARCHAR(180)  NULL,
    city                VARCHAR(100)  NULL,
    state               VARCHAR(100)  NULL,

    -- Either a known category, or free text when the enquirer picks "Other".
    -- Both nullable: the form should never reject a lead over a taxonomy gap.
    category_id         BIGINT        NULL,
    other_category      VARCHAR(120)  NULL,

    number_of_workers   INT           NULL,
    duration_months     INT           NULL,
    required_from       DATE          NULL,
    message             VARCHAR(2000) NULL,

    status              VARCHAR(20)   NOT NULL DEFAULT 'NEW'
        COMMENT 'NEW|CONTACTED|QUALIFIED|CONVERTED|CLOSED|SPAM',
    assigned_to_user_id BIGINT        NULL,
    internal_notes      VARCHAR(2000) NULL COMMENT 'staff only; never returned publicly',

    -- Captured for abuse investigation, not shown to staff screens by default.
    ip_address          VARCHAR(45)   NULL,
    user_agent          VARCHAR(300)  NULL,

    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    created_by          VARCHAR(150)  NULL,
    updated_by          VARCHAR(150)  NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    deleted             BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at          DATETIME(6)   NULL,
    deleted_by          VARCHAR(150)  NULL,

    CONSTRAINT pk_enquiries PRIMARY KEY (id),
    CONSTRAINT fk_enquiries_category FOREIGN KEY (category_id) REFERENCES manpower_categories (id),
    CONSTRAINT fk_enquiries_assigned_to FOREIGN KEY (assigned_to_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_enquiries_status ON enquiries (status, created_at);
CREATE INDEX ix_enquiries_created_at ON enquiries (created_at);
CREATE INDEX ix_enquiries_deleted ON enquiries (deleted);
CREATE INDEX ix_enquiries_assigned ON enquiries (assigned_to_user_id);

-- ---------------------------------------------------------------------
-- contact_messages - the general Contact Us form
-- ---------------------------------------------------------------------
CREATE TABLE contact_messages
(
    id          BIGINT        NOT NULL AUTO_INCREMENT,

    name        VARCHAR(120)  NOT NULL,
    email       VARCHAR(180)  NOT NULL,
    phone       VARCHAR(20)   NULL,
    subject     VARCHAR(200)  NULL,
    message     VARCHAR(2000) NOT NULL,

    read_flag   BOOLEAN       NOT NULL DEFAULT FALSE COMMENT '"read" is reserved in some SQL dialects',
    read_at     DATETIME(6)   NULL,
    replied     BOOLEAN       NOT NULL DEFAULT FALSE,
    spam        BOOLEAN       NOT NULL DEFAULT FALSE,

    ip_address  VARCHAR(45)   NULL,
    user_agent  VARCHAR(300)  NULL,

    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    created_by  VARCHAR(150)  NULL,
    updated_by  VARCHAR(150)  NULL,
    version     BIGINT        NOT NULL DEFAULT 0,
    deleted     BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at  DATETIME(6)   NULL,
    deleted_by  VARCHAR(150)  NULL,

    CONSTRAINT pk_contact_messages PRIMARY KEY (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_contact_messages_read ON contact_messages (read_flag, created_at);
CREATE INDEX ix_contact_messages_created_at ON contact_messages (created_at);

-- ---------------------------------------------------------------------
-- documents - every uploaded file, whatever owns it
--
-- Polymorphic owner (owner_type + owner_id) rather than a nullable FK per
-- owner table. Files hang off workers, clients, contracts, licences,
-- candidates and challans; a column per owner would mean widening this table
-- in most later phases.
--
-- The trade-off is no referential integrity on the owner, so DocumentService
-- must verify the owner exists before storing.
-- ---------------------------------------------------------------------
CREATE TABLE documents
(
    id                 BIGINT       NOT NULL AUTO_INCREMENT,

    owner_type         VARCHAR(30)  NOT NULL
        COMMENT 'COMPANY_LOGO|TESTIMONIAL|SERVICE_IMAGE|WORKER|CLIENT|CONTRACT|LICENCE|CANDIDATE|CHALLAN',
    owner_id           BIGINT       NULL COMMENT 'null for singletons such as the company logo',

    -- The name the user uploaded. Display only - never used to build a path,
    -- because it is attacker-controlled and could contain traversal sequences.
    original_file_name VARCHAR(255) NOT NULL,
    -- Server-generated key, the only thing used to locate the file on disk.
    storage_key        VARCHAR(400) NOT NULL,
    content_type       VARCHAR(100) NOT NULL COMMENT 'detected from content, not from the extension',
    size_bytes         BIGINT       NOT NULL,
    checksum_sha256    CHAR(64)     NULL COMMENT 'duplicate detection and integrity',

    -- Whether the file may be served without authentication. True only for
    -- genuinely public assets: the company logo, testimonial photos, service
    -- images. Never for identity documents.
    public_asset       BOOLEAN      NOT NULL DEFAULT FALSE,

    uploaded_by_user_id BIGINT      NULL,

    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NOT NULL,
    created_by         VARCHAR(150) NULL,
    updated_by         VARCHAR(150) NULL,
    version            BIGINT       NOT NULL DEFAULT 0,
    deleted            BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at         DATETIME(6)  NULL,
    deleted_by         VARCHAR(150) NULL,

    CONSTRAINT pk_documents PRIMARY KEY (id),
    CONSTRAINT uk_documents_storage_key UNIQUE (storage_key),
    CONSTRAINT fk_documents_uploaded_by FOREIGN KEY (uploaded_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_documents_owner ON documents (owner_type, owner_id);
CREATE INDEX ix_documents_deleted ON documents (deleted);
CREATE INDEX ix_documents_checksum ON documents (checksum_sha256);

-- =====================================================================
-- New permission: CONTENT_MANAGE
--
-- Covers the marketing site's editable content - services, industries,
-- testimonials and the manpower category master. Separate from
-- SETTINGS_MANAGE because editing a services page is routine marketing work,
-- whereas SETTINGS_MANAGE reaches statutory configuration and number series.
--
-- Must be mirrored in common/security/Permissions.java; PermissionCatalogueIT
-- fails the build otherwise.
-- =====================================================================
INSERT INTO permissions (name, module, description, created_at, updated_at, created_by, updated_by, version)
VALUES ('CONTENT_MANAGE', 'CONTENT',
        'Edit public website content: services, industries, testimonials and manpower categories',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0);

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE p.name = 'CONTENT_MANAGE'
  AND r.name IN ('SUPER_ADMIN', 'ADMIN');

-- =====================================================================
-- Seed: manpower categories, from the brief's examples
-- =====================================================================
INSERT INTO manpower_categories (code, name, skill_level, description, display_order, active,
                                 created_at, updated_at, created_by, updated_by, version)
VALUES ('SECURITY_GUARD', 'Security Guard', 'SEMI_SKILLED',
        'Unarmed static guarding, gate control and patrolling', 10, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('SECURITY_SUPERVISOR', 'Security Supervisor', 'SKILLED',
        'Shift supervision of a guard detail', 20, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('HELPER', 'Helper', 'UNSKILLED',
        'General loading, shifting and assistance', 30, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('HOUSEKEEPING', 'Housekeeping Staff', 'UNSKILLED',
        'Cleaning and facility upkeep', 40, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('MACHINE_OPERATOR', 'Machine Operator', 'SKILLED',
        'Operation of production and packaging machinery', 50, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('ELECTRICIAN', 'Electrician', 'SKILLED',
        'Electrical installation and maintenance (ITI qualified)', 60, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('WELDER', 'Welder', 'SKILLED',
        'Arc and gas welding', 70, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('TECHNICIAN', 'Maintenance Technician', 'HIGHLY_SKILLED',
        'Mechanical and electrical plant maintenance', 80, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('DRIVER', 'Driver', 'SEMI_SKILLED',
        'Light and heavy vehicle driving with a valid licence', 90, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('DATA_ENTRY', 'Data Entry Operator', 'SEMI_SKILLED',
        'Back-office data entry and document handling', 100, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('OFFICE_ASSISTANT', 'Office Assistant', 'SEMI_SKILLED',
        'Clerical and administrative support', 110, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('SITE_SUPERVISOR', 'Site Supervisor', 'HIGHLY_SKILLED',
        'On-site supervision of a deployed contract workforce', 120, TRUE,
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0);

-- =====================================================================
-- Seed: the six services from the brief.
-- Published TRUE so the public endpoints return something immediately; the
-- copy is placeholder and should be rewritten from the admin API.
-- =====================================================================
INSERT INTO service_offerings (slug, title, summary, description, icon, display_order, published,
                               meta_title, meta_description,
                               created_at, updated_at, created_by, updated_by, version)
VALUES ('manpower-supply', 'Manpower Supply',
        'Contract workforce for factories, warehouses, mines and hospitals - deployed, supervised and compliant.',
        'We supply skilled, semi-skilled and unskilled contract labour across India, handling sourcing, verification, deployment, attendance and statutory compliance so you deal with one vendor instead of a dozen.',
        'users', 10, TRUE,
        'Contract Manpower Supply Services in India',
        'Supply of security guards, helpers, operators and technicians on contract, with full PF, ESI and PT compliance.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

       ('security-services', 'Security Services',
        'PSARA-licensed security guards and supervisors for industrial, commercial and institutional sites.',
        'Trained and police-verified security personnel, deployed in shifts with supervisory oversight, attendance monitoring and incident reporting.',
        'shield', 20, TRUE,
        'PSARA Licensed Security Guard Services',
        'Deploy trained, police-verified security guards and supervisors under a PSARA licence.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

       ('recruitment-and-placement', 'Recruitment & Placement',
        'Permanent and contract hiring - sourcing, screening, interviews and onboarding.',
        'End-to-end recruitment: we source candidates, screen and verify them, coordinate interviews and manage onboarding, for both permanent roles and contract positions.',
        'user-check', 30, TRUE,
        'Recruitment and Placement Services',
        'Permanent and contract recruitment with screening, verification and onboarding handled for you.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

       ('payroll-outsourcing', 'Payroll Outsourcing',
        'Monthly payroll processing, payslips, bank transfers and statutory deductions.',
        'We run your monthly payroll: attendance capture, wage computation, PF, ESI, professional tax and labour welfare fund deductions, payslip generation, bank transfer files and statutory returns.',
        'calculator', 40, TRUE,
        'Payroll Outsourcing and Processing Services',
        'Outsource monthly payroll with PF, ESI and PT deductions, payslips and bank transfer files.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

       ('statutory-compliance', 'Statutory Compliance',
        'PF, ESI, professional tax, contract labour licensing and registers - filed and audit-ready.',
        'We maintain your statutory position: PF and ESI registration and monthly challans, professional tax, labour welfare fund, Contract Labour (R&A) Act licensing, and the wage and attendance registers an inspector will ask for.',
        'file-check', 50, TRUE,
        'Statutory Compliance Services - PF, ESI, PT',
        'PF, ESI, professional tax and contract labour compliance, with registers kept audit-ready.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

       ('housekeeping-and-facility-management', 'Housekeeping & Facility Management',
        'Cleaning, upkeep and facility support staff with supervision and consumables.',
        'Housekeeping teams for offices, plants and hospitals, including supervision, rostering, consumables management and periodic deep-cleaning schedules.',
        'sparkles', 60, TRUE,
        'Housekeeping and Facility Management Services',
        'Housekeeping and facility support staff with supervision, rostering and consumables.',
        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0);

-- =====================================================================
-- Seed: industries served, from the brief
-- =====================================================================
INSERT INTO industries (slug, name, description, icon, display_order, published,
                        created_at, updated_at, created_by, updated_by, version)
VALUES ('manufacturing', 'Manufacturing & Factories',
        'Production line operators, machine operators, helpers and maintenance technicians.',
        'factory', 10, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('mining', 'Mining',
        'Site labour, equipment operators and safety-compliant security for mine premises.',
        'mountain', 20, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('healthcare', 'Hospitals & Healthcare',
        'Housekeeping, patient attendants, security and front-desk staff for hospitals.',
        'hospital', 30, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('warehousing-logistics', 'Warehousing & Logistics',
        'Loaders, pickers, packers, forklift operators and warehouse security.',
        'truck', 40, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('government', 'Government Departments',
        'Outsourced staffing for government offices and public undertakings, on tender terms.',
        'landmark', 50, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('commercial-retail', 'Commercial & Retail',
        'Security, housekeeping and front-of-house staff for offices, malls and retail.',
        'building', 60, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('construction', 'Construction & Infrastructure',
        'Site labour, skilled trades and site security for construction projects.',
        'hard-hat', 70, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('hospitality', 'Hotels & Hospitality',
        'Housekeeping, stewarding, kitchen helpers and security for hotels.',
        'concierge-bell', 80, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0);
