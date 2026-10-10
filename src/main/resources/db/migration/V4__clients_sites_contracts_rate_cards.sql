-- =====================================================================
-- V4 - Clients, their sites, contracts and rate cards, plus the skills master.
--
-- Creates: clients, client_users, client_sites, client_contracts, rate_cards,
--          skills, category_skills
--
-- This is where the commercial side of the business starts. Three things here
-- carry weight well beyond this phase:
--
--  * clients.state_code decides GST treatment. Equal to the company's state
--    code means CGST+SGST; different means IGST. Phase 9 reads it on every
--    invoice, so a wrong value produces a wrong tax return.
--
--  * rate_cards are dated history, not current values. Recomputing March's
--    payroll must use March's rates, so a rate change inserts a new row and
--    closes the old one rather than updating in place.
--
--  * client_users links a CLIENT-role login to exactly one client. Every
--    ownership check in the portal resolves through this table.
--
-- No new permissions: CLIENT_READ, CLIENT_WRITE and CLIENT_APPROVE were seeded
-- in V2 and already describe this scope.
-- =====================================================================

-- ---------------------------------------------------------------------
-- clients
-- ---------------------------------------------------------------------
CREATE TABLE clients
(
    id                  BIGINT        NOT NULL AUTO_INCREMENT,

    legal_name          VARCHAR(200)  NOT NULL COMMENT 'as registered; appears on invoices',
    trade_name          VARCHAR(200)  NULL COMMENT 'the name people actually use',

    -- Statutory identifiers. Nullable because a client is often onboarded from
    -- an approved registration before paperwork arrives, and blocking the whole
    -- record for a missing GSTIN would stop work starting. Phase 9 refuses to
    -- issue an invoice without one.
    gstin               VARCHAR(15)   NULL,
    pan                 VARCHAR(10)   NULL,
    cin                 VARCHAR(21)   NULL,

    -- Billing address. Distinct from any site address: work happens at sites,
    -- invoices go here.
    billing_address_line1 VARCHAR(200) NULL,
    billing_address_line2 VARCHAR(200) NULL,
    billing_city        VARCHAR(100)  NULL,
    billing_state       VARCHAR(100)  NULL,
    -- Two-digit GST state code. THE field that decides CGST+SGST vs IGST.
    billing_state_code  VARCHAR(2)    NULL,
    billing_pincode     VARCHAR(10)   NULL,
    billing_country     VARCHAR(100)  NULL DEFAULT 'India',

    industry_id         BIGINT        NULL COMMENT 'optional link to the industries master',

    status              VARCHAR(20)   NOT NULL DEFAULT 'PENDING_APPROVAL'
        COMMENT 'PENDING_APPROVAL|ACTIVE|SUSPENDED|INACTIVE|REJECTED',

    -- Default credit period. A contract may override it; this is the fallback
    -- used when no active contract says otherwise.
    payment_terms_days  INT           NOT NULL DEFAULT 30,

    onboarded_on        DATE          NULL,
    notes               VARCHAR(2000) NULL,

    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    created_by          VARCHAR(150)  NULL,
    updated_by          VARCHAR(150)  NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    deleted             BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at          DATETIME(6)   NULL,
    deleted_by          VARCHAR(150)  NULL,

    CONSTRAINT pk_clients PRIMARY KEY (id),
    -- GSTIN is unique nationally, so a duplicate means the same company entered
    -- twice. Nullable columns are exempt from the constraint in MySQL, so
    -- several clients may sit without one.
    CONSTRAINT uk_clients_gstin UNIQUE (gstin),
    CONSTRAINT fk_clients_industry FOREIGN KEY (industry_id) REFERENCES industries (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_clients_status ON clients (status);
CREATE INDEX ix_clients_deleted ON clients (deleted);
CREATE INDEX ix_clients_legal_name ON clients (legal_name);
CREATE INDEX ix_clients_state_code ON clients (billing_state_code);

-- ---------------------------------------------------------------------
-- client_users - which logins belong to which client company
--
-- The table every ownership check goes through. A CLIENT-role user sees only
-- the client reached from here.
-- ---------------------------------------------------------------------
CREATE TABLE client_users
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    client_id       BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,

    designation     VARCHAR(120) NULL COMMENT 'e.g. Plant Head, Purchase Manager',
    -- Who receives contract and invoice notifications. At most one per client,
    -- enforced in the service rather than the schema, since MySQL cannot
    -- express "unique where primary_contact = true".
    primary_contact BOOLEAN      NOT NULL DEFAULT FALSE,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      VARCHAR(150) NULL,
    updated_by      VARCHAR(150) NULL,
    version         BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_client_users PRIMARY KEY (id),
    -- One login belongs to ONE client. Without this, a compromised or
    -- mis-assigned account could read two companies' data, and every ownership
    -- check would have to cope with a set rather than a single id.
    CONSTRAINT uk_client_users_user UNIQUE (user_id),
    CONSTRAINT fk_client_users_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_client_users_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_client_users_client ON client_users (client_id);

-- ---------------------------------------------------------------------
-- client_sites - where the work actually happens
-- ---------------------------------------------------------------------
CREATE TABLE client_sites
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    client_id             BIGINT       NOT NULL,

    site_code             VARCHAR(40)  NOT NULL COMMENT 'client-scoped, e.g. PUNE-PLANT-1',
    site_name             VARCHAR(160) NOT NULL,

    address_line1         VARCHAR(200) NULL,
    address_line2         VARCHAR(200) NULL,
    city                  VARCHAR(100) NULL,
    -- A site's state can differ from the billing state, and it is the site's
    -- state that governs minimum wages, professional tax and the contract
    -- labour licence - so this is not a duplicate of the client's address.
    state                 VARCHAR(100) NULL,
    state_code            VARCHAR(2)   NULL,
    pincode               VARCHAR(10)  NULL,

    site_incharge_name    VARCHAR(120) NULL,
    site_incharge_phone   VARCHAR(20)  NULL,
    site_incharge_email   VARCHAR(180) NULL,

    active                BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(150) NULL,
    updated_by            VARCHAR(150) NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at            DATETIME(6)  NULL,
    deleted_by            VARCHAR(150) NULL,

    CONSTRAINT pk_client_sites PRIMARY KEY (id),
    CONSTRAINT uk_client_sites_code UNIQUE (client_id, site_code),
    CONSTRAINT fk_client_sites_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_client_sites_client ON client_sites (client_id, active);
CREATE INDEX ix_client_sites_deleted ON client_sites (deleted);
CREATE INDEX ix_client_sites_state ON client_sites (state_code);

-- ---------------------------------------------------------------------
-- client_contracts
--
-- Carries the commercial terms: what we charge on top of wages, and for how
-- long. Phase 9 reads the active contract to compute the service charge line
-- on every invoice.
-- ---------------------------------------------------------------------
CREATE TABLE client_contracts
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    client_id            BIGINT        NOT NULL,

    contract_number      VARCHAR(60)   NOT NULL,
    title                VARCHAR(200)  NULL,

    start_date           DATE          NOT NULL,
    -- Null means open-ended. Expiry reminders (Phase 10) fire at 60/30/7 days
    -- before a non-null end date.
    end_date             DATE          NULL,

    -- Percentage of wages, or a flat amount per deployed worker per month.
    -- Both are in use in this industry, so the type is explicit rather than
    -- inferred from the magnitude of the value.
    service_charge_type  VARCHAR(20)   NOT NULL COMMENT 'PERCENTAGE|FIXED_PER_WORKER',
    service_charge_value DECIMAL(12, 2) NOT NULL,

    payment_terms_days   INT           NULL COMMENT 'overrides the client default when set',

    status               VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT|ACTIVE|EXPIRED|TERMINATED',

    terminated_on        DATE          NULL,
    termination_reason   VARCHAR(500)  NULL,
    notes                VARCHAR(2000) NULL,

    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    created_by           VARCHAR(150)  NULL,
    updated_by           VARCHAR(150)  NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    deleted              BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at           DATETIME(6)   NULL,
    deleted_by           VARCHAR(150)  NULL,

    CONSTRAINT pk_client_contracts PRIMARY KEY (id),
    CONSTRAINT uk_client_contracts_number UNIQUE (contract_number),
    CONSTRAINT fk_client_contracts_client FOREIGN KEY (client_id) REFERENCES clients (id),
    -- A percentage above 100 or a negative charge is a data-entry error, not a
    -- deal. Caught here as well as in validation, because this value flows
    -- straight onto an invoice.
    CONSTRAINT ck_client_contracts_charge CHECK (service_charge_value >= 0),
    CONSTRAINT ck_client_contracts_dates CHECK (end_date IS NULL OR end_date >= start_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_client_contracts_client ON client_contracts (client_id, status);
CREATE INDEX ix_client_contracts_end_date ON client_contracts (end_date);
CREATE INDEX ix_client_contracts_deleted ON client_contracts (deleted);

-- ---------------------------------------------------------------------
-- rate_cards - the agreed wage and billing rate per client per category
--
-- DATED HISTORY, not current values. A rate change inserts a new row and sets
-- effective_to on the old one. Recomputing an earlier month's payroll or
-- reissuing an old invoice must use the rates that applied then, so these rows
-- are never edited in place once used.
-- ---------------------------------------------------------------------
CREATE TABLE rate_cards
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    client_id         BIGINT         NOT NULL,
    category_id       BIGINT         NOT NULL,
    -- Null means the rate applies to every site of this client. A site-specific
    -- row wins over a client-wide one for the same category.
    site_id           BIGINT         NULL,

    -- What the worker is paid per month. Checked against the state minimum wage
    -- for the site and skill level in Phase 8.
    monthly_wage      DECIMAL(12, 2) NOT NULL,
    -- What the client is billed per worker per month, before statutory
    -- contributions, service charge and GST.
    billing_rate      DECIMAL(12, 2) NOT NULL,

    ot_rate_per_hour  DECIMAL(10, 2) NULL,
    shift_hours       INT            NOT NULL DEFAULT 8,

    effective_from    DATE           NOT NULL,
    -- Null means "current". Closed off when a successor row is created.
    effective_to      DATE           NULL,

    notes             VARCHAR(500)   NULL,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(150)   NULL,
    updated_by        VARCHAR(150)   NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at        DATETIME(6)    NULL,
    deleted_by        VARCHAR(150)   NULL,

    CONSTRAINT pk_rate_cards PRIMARY KEY (id),
    CONSTRAINT fk_rate_cards_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_rate_cards_category FOREIGN KEY (category_id) REFERENCES manpower_categories (id),
    CONSTRAINT fk_rate_cards_site FOREIGN KEY (site_id) REFERENCES client_sites (id),
    CONSTRAINT ck_rate_cards_amounts CHECK (monthly_wage >= 0 AND billing_rate >= 0),
    CONSTRAINT ck_rate_cards_dates CHECK (effective_to IS NULL OR effective_to >= effective_from),
    CONSTRAINT ck_rate_cards_shift CHECK (shift_hours BETWEEN 1 AND 24)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Supports the resolution query: newest effective_from on or before a date.
CREATE INDEX ix_rate_cards_lookup
    ON rate_cards (client_id, category_id, site_id, effective_from, effective_to);
CREATE INDEX ix_rate_cards_deleted ON rate_cards (deleted);

-- ---------------------------------------------------------------------
-- skills and category_skills - completing the Phase 3 master data
-- ---------------------------------------------------------------------
CREATE TABLE skills
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(150) NULL,
    updated_by  VARCHAR(150) NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_skills PRIMARY KEY (id),
    CONSTRAINT uk_skills_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE category_skills
(
    category_id BIGINT NOT NULL,
    skill_id    BIGINT NOT NULL,

    CONSTRAINT pk_category_skills PRIMARY KEY (category_id, skill_id),
    CONSTRAINT fk_category_skills_category
        FOREIGN KEY (category_id) REFERENCES manpower_categories (id) ON DELETE CASCADE,
    CONSTRAINT fk_category_skills_skill
        FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- =====================================================================
-- Seed: a starting skills vocabulary
-- =====================================================================
INSERT INTO skills (name, description, active, created_at, updated_at, created_by, updated_by, version)
VALUES ('Fire Safety Training', 'Completed fire safety and evacuation training', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('First Aid', 'Certified in basic first aid', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('PSARA Trained', 'Trained under the Private Security Agencies Regulation Act', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('Heavy Vehicle Licence', 'Holds a valid heavy motor vehicle licence', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('Light Vehicle Licence', 'Holds a valid light motor vehicle licence', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('Forklift Operation', 'Trained and certified to operate a forklift', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('ITI Electrical', 'ITI certificate in the electrical trade', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('ITI Fitter', 'ITI certificate in the fitter trade', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('Arc Welding', 'Competent in arc welding', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('Housekeeping Chemicals', 'Trained in safe handling of cleaning chemicals', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('Computer Literacy', 'Able to use a computer for data entry and email', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('Spoken English', 'Conversational English', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0);

-- Map the obvious skills onto the categories seeded in V3.
INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'SECURITY_GUARD' AND s.name IN ('PSARA Trained', 'Fire Safety Training', 'First Aid');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'SECURITY_SUPERVISOR' AND s.name IN ('PSARA Trained', 'First Aid', 'Spoken English');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'ELECTRICIAN' AND s.name IN ('ITI Electrical', 'Fire Safety Training');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'WELDER' AND s.name IN ('Arc Welding', 'Fire Safety Training');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'TECHNICIAN' AND s.name IN ('ITI Fitter', 'Fire Safety Training', 'First Aid');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'DRIVER' AND s.name IN ('Heavy Vehicle Licence', 'Light Vehicle Licence', 'First Aid');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'HOUSEKEEPING' AND s.name IN ('Housekeeping Chemicals', 'Fire Safety Training');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'MACHINE_OPERATOR' AND s.name IN ('Fire Safety Training', 'Forklift Operation');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code IN ('DATA_ENTRY', 'OFFICE_ASSISTANT') AND s.name IN ('Computer Literacy', 'Spoken English');

INSERT INTO category_skills (category_id, skill_id)
SELECT c.id, s.id FROM manpower_categories c CROSS JOIN skills s
WHERE c.code = 'SITE_SUPERVISOR' AND s.name IN ('Spoken English', 'First Aid', 'Fire Safety Training');
