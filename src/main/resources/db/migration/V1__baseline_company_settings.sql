-- =====================================================================
-- V1 - Baseline.
--
-- Establishes the Flyway history for this database and creates the company
-- profile table. Every table from here on carries the same six audit columns
-- (id, created_at, updated_at, created_by, updated_by, version) because every
-- entity extends BaseEntity, and spring.jpa.hibernate.ddl-auto=validate will
-- refuse to start the application if a table and its entity disagree.
--
-- Phase 2 adds V2 with users, roles, permissions and refresh tokens.
-- =====================================================================

CREATE TABLE company_settings
(
    id                     BIGINT        NOT NULL AUTO_INCREMENT,

    -- ---------- Identity ----------
    legal_name             VARCHAR(200)  NOT NULL,
    trade_name             VARCHAR(200)  NULL,
    tagline                VARCHAR(300)  NULL,
    about                  VARCHAR(2000) NULL,

    -- ---------- Registered address ----------
    address_line1          VARCHAR(200)  NULL,
    address_line2          VARCHAR(200)  NULL,
    city                   VARCHAR(100)  NULL,
    state                  VARCHAR(100)  NULL,
    -- Two-digit GST state code. Compared against the client's code to decide
    -- whether an invoice carries CGST+SGST (equal) or IGST (different).
    state_code             VARCHAR(2)    NULL,
    pincode                VARCHAR(10)   NULL,
    country                VARCHAR(100)  NULL,

    -- ---------- Contact ----------
    phone                  VARCHAR(20)   NULL,
    alternate_phone        VARCHAR(20)   NULL,
    email                  VARCHAR(180)  NULL,
    support_email          VARCHAR(180)  NULL,
    website                VARCHAR(200)  NULL,

    -- ---------- Statutory registrations ----------
    gstin                  VARCHAR(15)   NULL,
    pan                    VARCHAR(10)   NULL,
    tan                    VARCHAR(10)   NULL,
    cin                    VARCHAR(21)   NULL,
    pf_establishment_code  VARCHAR(30)   NULL,
    esi_establishment_code VARCHAR(30)   NULL,
    pt_registration_number VARCHAR(30)   NULL,

    -- ---------- Bank details, printed on invoices ----------
    bank_name              VARCHAR(120)  NULL,
    bank_branch            VARCHAR(120)  NULL,
    bank_account_number    VARCHAR(30)   NULL,
    bank_ifsc              VARCHAR(11)   NULL,

    -- ---------- Branding ----------
    logo_path              VARCHAR(400)  NULL,

    -- ---------- Social links ----------
    linkedin_url           VARCHAR(300)  NULL,
    facebook_url           VARCHAR(300)  NULL,
    twitter_url            VARCHAR(300)  NULL,
    instagram_url          VARCHAR(300)  NULL,

    -- ---------- Audit, from BaseEntity ----------
    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    created_by             VARCHAR(150)  NULL,
    updated_by             VARCHAR(150)  NULL,
    version                BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_company_settings PRIMARY KEY (id)
    -- The company profile is a singleton, but that invariant cannot be expressed
    -- here: MySQL rejects a CHECK constraint that refers to an AUTO_INCREMENT
    -- column. It is enforced in CompanySettingsService instead, which only ever
    -- reads and writes the row with id = CompanySettingsService.SINGLETON_ID.
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Seed the one row.
--
-- These are PLACEHOLDERS. Replace them with the real company details either
-- through PUT /api/v1/settings/company or with a direct UPDATE. The GSTIN, PAN
-- and state code in particular must be correct before any invoice is raised:
-- state_code drives the CGST+SGST vs IGST decision in Phase 9.
-- ---------------------------------------------------------------------

INSERT INTO company_settings (id,
                              legal_name,
                              trade_name,
                              tagline,
                              about,
                              address_line1,
                              city,
                              state,
                              state_code,
                              pincode,
                              country,
                              phone,
                              email,
                              website,
                              gstin,
                              pan,
                              created_at,
                              updated_at,
                              created_by,
                              updated_by,
                              version)
VALUES (1,
        'Your Company Private Limited',
        'Your Company',
        'Manpower supply, recruitment and statutory compliance across India',
        'Replace this text with the company profile shown on the public website.',
        'Registered office address line 1',
        'Pune',
        'Maharashtra',
        '27',
        '411001',
        'India',
        '+91 00000 00000',
        'info@example.com',
        'https://www.example.com',
        NULL,
        NULL,
        UTC_TIMESTAMP(6),
        UTC_TIMESTAMP(6),
        'flyway',
        'flyway',
        0);
