-- =====================================================================
-- V8 - The supply side: client demand, the workforce, and postings.
--
-- Creates: requisitions, requisition_status_history, workers,
--          worker_bank_details, worker_documents, salary_structures,
--          deployments
-- Alters:  jobs (adds the requisition link promised by V6)
--
-- This is where the business actually happens: a client asks for N workers at
-- a site, recruitment supplies people, and a deployment posts a worker there.
-- Five things here carry weight well beyond this phase.
--
--  * ENCRYPTED COLUMNS. aadhaar_encrypted, pan_encrypted and
--    account_number_encrypted hold AES-256-GCM ciphertext with a random IV,
--    written through a JPA AttributeConverter keyed by FIELD_ENCRYPTION_KEY.
--    The matching *_hash columns are HMAC-SHA256 blind indexes, and they exist
--    because a random IV makes the ciphertext non-deterministic: equality
--    lookups are impossible against it. Duplicate detection runs on the hash.
--    See docs/decisions.md for why a deterministic cipher was refused.
--
--    The _encrypted columns are sized for ciphertext, not plaintext: a 12-byte
--    IV plus a 16-byte tag plus the payload, base64-encoded, plus the 'v1:'
--    prefix. VARCHAR(255) is generous for a 12-character PAN.
--
--  * requisitions.site_id is NOT NULL. A requisition is "N workers at THIS
--    site from THIS date". Without the site there is no state code, so no
--    minimum wage, no professional tax slab and no rate card to resolve - the
--    record could not be costed or fulfilled. Better to refuse it than to
--    store a requirement nobody can act on.
--
--  * No stored fulfilment counter. quantity_required is stored;
--    "how many are deployed" is counted from deployments on read, the same
--    decision as job openings in V6. A stored counter drifts the moment a
--    deployment is released.
--
--  * deployments must never overlap for one worker. A person cannot be at two
--    client sites at once, and overlapping rows would double-bill the client
--    and double-count attendance in Phase 7. Enforced in DeploymentService
--    using DateUtils.overlaps, which treats a null end_date as open-ended.
--    MySQL cannot express that as a CHECK constraint.
--
--  * salary_structures are dated history, exactly like rate_cards in V4.
--    Recomputing March's payroll must use March's structure, so a change
--    inserts a row and closes the previous one rather than updating in place.
--
-- No new permissions. WORKER_READ, WORKER_WRITE, WORKER_SENSITIVE_READ,
-- REQUISITION_READ, REQUISITION_CREATE, REQUISITION_APPROVE, DEPLOYMENT_READ
-- and DEPLOYMENT_MANAGE were all seeded in V2 and already describe this scope.
-- =====================================================================

-- ---------------------------------------------------------------------
-- requisitions - a client's request for workers
-- ---------------------------------------------------------------------
CREATE TABLE requisitions
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,

    client_id             BIGINT       NOT NULL,
    -- NOT NULL on purpose - see the header note.
    site_id               BIGINT       NOT NULL,
    category_id           BIGINT       NOT NULL COMMENT 'which kind of worker is wanted',

    quantity_required     INT          NOT NULL,

    -- Overrides the category's default when the client wants a specific grade.
    skill_level           VARCHAR(20)  NULL
        COMMENT 'UNSKILLED|SEMI_SKILLED|SKILLED|HIGHLY_SKILLED',

    shift                 VARCHAR(20)  NULL COMMENT 'DAY|NIGHT|ROTATIONAL',

    required_from         DATE         NOT NULL,
    required_to           DATE         NULL COMMENT 'null means ongoing',

    status                VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT|SUBMITTED|APPROVED|IN_PROGRESS|PARTIALLY_FULFILLED|FULFILLED|CLOSED|REJECTED|CANCELLED',

    -- Who asked. May be a client portal user or a member of staff raising it on
    -- the client's behalf after a phone call.
    raised_by_user_id     BIGINT       NULL,
    approved_by_user_id   BIGINT       NULL,
    approved_at           DATETIME(6)  NULL,

    -- Only meaningful in REJECTED, kept separate from the history note so the
    -- current reason reads without walking the trail.
    rejection_reason      VARCHAR(500) NULL,

    notes                 VARCHAR(2000) NULL,

    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(150) NULL,
    updated_by            VARCHAR(150) NULL,
    version               BIGINT       NOT NULL DEFAULT 0,
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at            DATETIME(6)  NULL,
    deleted_by            VARCHAR(150) NULL,

    CONSTRAINT pk_requisitions PRIMARY KEY (id),
    CONSTRAINT fk_requisitions_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_requisitions_site FOREIGN KEY (site_id) REFERENCES client_sites (id),
    CONSTRAINT fk_requisitions_category FOREIGN KEY (category_id)
        REFERENCES manpower_categories (id),
    CONSTRAINT fk_requisitions_raiser FOREIGN KEY (raised_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_requisitions_approver FOREIGN KEY (approved_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_requisitions_quantity CHECK (quantity_required >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_requisitions_client_status ON requisitions (client_id, status);
CREATE INDEX ix_requisitions_site ON requisitions (site_id);
CREATE INDEX ix_requisitions_status ON requisitions (status);
CREATE INDEX ix_requisitions_required_from ON requisitions (required_from);
CREATE INDEX ix_requisitions_deleted ON requisitions (deleted);

-- ---------------------------------------------------------------------
-- requisition_status_history - append-only, like application_status_history
-- ---------------------------------------------------------------------
CREATE TABLE requisition_status_history
(
    id                 BIGINT        NOT NULL AUTO_INCREMENT,

    requisition_id     BIGINT        NOT NULL,

    from_status        VARCHAR(30)   NULL COMMENT 'null on the row recording creation',
    to_status          VARCHAR(30)   NOT NULL,

    changed_by_user_id BIGINT        NULL,
    changed_by_email   VARCHAR(180)  NULL COMMENT 'denormalised so the trail outlives the user',

    remarks            VARCHAR(1000) NULL,

    created_at         DATETIME(6)   NOT NULL,

    CONSTRAINT pk_requisition_status_history PRIMARY KEY (id),
    CONSTRAINT fk_rsh_requisition FOREIGN KEY (requisition_id) REFERENCES requisitions (id),
    CONSTRAINT fk_rsh_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_rsh_requisition ON requisition_status_history (requisition_id, created_at);

-- ---------------------------------------------------------------------
-- jobs.requisition_id - the link V6 promised
-- ---------------------------------------------------------------------
ALTER TABLE jobs
    ADD COLUMN requisition_id BIGINT NULL
        COMMENT 'the client demand this advert sources for, when there is one'
        AFTER site_id,
    ADD CONSTRAINT fk_jobs_requisition FOREIGN KEY (requisition_id) REFERENCES requisitions (id);

CREATE INDEX ix_jobs_requisition ON jobs (requisition_id);

-- ---------------------------------------------------------------------
-- workers
--
-- employee_code is NOT stored. It is derived as EMP-{joining year}-{id}, the
-- same reasoning as clients.client_code in V4: a second source of truth for an
-- identifier is a second thing that can disagree. It is not a statutory series
-- and does not need to be gapless.
-- ---------------------------------------------------------------------
CREATE TABLE workers
(
    id                          BIGINT       NOT NULL AUTO_INCREMENT,

    -- The WORKER-role login, once the worker has one. Most operatives never do.
    user_id                     BIGINT       NULL,
    -- Set when a JOINED application is converted into a worker, which is how
    -- Phase 10 shows the principal employer that a hire was vetted.
    candidate_id                BIGINT       NULL,

    first_name                  VARCHAR(100) NOT NULL,
    last_name                   VARCHAR(100) NULL,
    father_or_husband_name      VARCHAR(160) NULL
        COMMENT 'required on the CLRA and PF registers',

    date_of_birth               DATE         NULL,
    gender                      VARCHAR(10)  NULL COMMENT 'MALE|FEMALE|OTHER',
    date_of_joining             DATE         NOT NULL,

    phone                       VARCHAR(20)  NULL,
    email                       VARCHAR(180) NULL,

    address_line1               VARCHAR(200) NULL,
    address_line2               VARCHAR(200) NULL,
    city                        VARCHAR(100) NULL,
    state                       VARCHAR(100) NULL,
    pincode                     VARCHAR(10)  NULL,

    -- ---- Encrypted. See the header note. ----
    aadhaar_encrypted           VARCHAR(255) NULL COMMENT 'AES-256-GCM, random IV',
    aadhaar_hash                CHAR(64)     NULL COMMENT 'HMAC-SHA256 blind index for lookups',
    pan_encrypted               VARCHAR(255) NULL COMMENT 'AES-256-GCM, random IV',
    pan_hash                    CHAR(64)     NULL COMMENT 'HMAC-SHA256 blind index for lookups',

    -- Statutory identifiers. Not secret, so stored in clear: they appear on
    -- every monthly return and are useless without the underlying account.
    uan                         VARCHAR(12)  NULL COMMENT 'PF universal account number',
    esic_ip_number              VARCHAR(10)  NULL,

    status                      VARCHAR(20)  NOT NULL DEFAULT 'ON_BENCH'
        COMMENT 'ON_BENCH|DEPLOYED|ON_LEAVE|RESIGNED|TERMINATED',

    police_verification_status  VARCHAR(20)  NOT NULL DEFAULT 'NOT_STARTED'
        COMMENT 'NOT_STARTED|IN_PROGRESS|VERIFIED|FAILED|NOT_REQUIRED',
    police_verification_date    DATE         NULL,

    emergency_contact_name      VARCHAR(160) NULL,
    emergency_contact_phone     VARCHAR(20)  NULL,
    emergency_contact_relation  VARCHAR(60)  NULL,

    last_working_day            DATE         NULL,
    exit_reason                 VARCHAR(500) NULL,
    full_and_final_settled      BOOLEAN      NOT NULL DEFAULT FALSE,

    notes                       VARCHAR(2000) NULL,

    created_at                  DATETIME(6)  NOT NULL,
    updated_at                  DATETIME(6)  NOT NULL,
    created_by                  VARCHAR(150) NULL,
    updated_by                  VARCHAR(150) NULL,
    version                     BIGINT       NOT NULL DEFAULT 0,
    deleted                     BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at                  DATETIME(6)  NULL,
    deleted_by                  VARCHAR(150) NULL,

    CONSTRAINT pk_workers PRIMARY KEY (id),
    CONSTRAINT uk_workers_user UNIQUE (user_id),
    CONSTRAINT uk_workers_candidate UNIQUE (candidate_id),
    -- Duplicate detection runs on the blind index, never on the ciphertext.
    -- Nullable columns are exempt in MySQL, so workers without an Aadhaar on
    -- file do not collide with each other.
    CONSTRAINT uk_workers_aadhaar_hash UNIQUE (aadhaar_hash),
    CONSTRAINT uk_workers_pan_hash UNIQUE (pan_hash),
    CONSTRAINT uk_workers_uan UNIQUE (uan),
    CONSTRAINT fk_workers_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_workers_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_workers_status ON workers (status);
CREATE INDEX ix_workers_name ON workers (first_name, last_name);
CREATE INDEX ix_workers_phone ON workers (phone);
CREATE INDEX ix_workers_joining ON workers (date_of_joining);
CREATE INDEX ix_workers_deleted ON workers (deleted);
CREATE INDEX ix_workers_police_verification ON workers (police_verification_status);

-- ---------------------------------------------------------------------
-- worker_bank_details - where wages are paid
-- ---------------------------------------------------------------------
CREATE TABLE worker_bank_details
(
    id                        BIGINT       NOT NULL AUTO_INCREMENT,

    worker_id                 BIGINT       NOT NULL,

    account_number_encrypted  VARCHAR(255) NOT NULL COMMENT 'AES-256-GCM, random IV',
    account_number_hash       CHAR(64)     NOT NULL COMMENT 'HMAC-SHA256 blind index',

    ifsc                      VARCHAR(11)  NOT NULL,
    bank_name                 VARCHAR(120) NULL,
    branch                    VARCHAR(120) NULL,
    -- Phase 8's bank transfer file needs the name as the bank holds it, which
    -- is not always the name on the worker record.
    account_holder_name       VARCHAR(160) NULL,

    -- Exactly one primary per worker. Enforced in the service, not here:
    -- MySQL has no partial unique index, so "unique where primary_account is
    -- true" cannot be expressed.
    primary_account           BOOLEAN      NOT NULL DEFAULT FALSE,

    active                    BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at                DATETIME(6)  NOT NULL,
    updated_at                DATETIME(6)  NOT NULL,
    created_by                VARCHAR(150) NULL,
    updated_by                VARCHAR(150) NULL,
    version                   BIGINT       NOT NULL DEFAULT 0,
    deleted                   BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at                DATETIME(6)  NULL,
    deleted_by                VARCHAR(150) NULL,

    CONSTRAINT pk_worker_bank_details PRIMARY KEY (id),
    CONSTRAINT fk_wbd_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    -- The same account must not be registered twice for one worker.
    CONSTRAINT uk_wbd_worker_account UNIQUE (worker_id, account_number_hash)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_wbd_worker ON worker_bank_details (worker_id, primary_account);
CREATE INDEX ix_wbd_deleted ON worker_bank_details (deleted);

-- ---------------------------------------------------------------------
-- worker_documents - the evidence file
--
-- The file itself lives in `documents` (V3), so storage, checksums, content
-- type detection and the download endpoint are all reused. This table adds
-- what a worker document means: its type, whether anyone has checked it, and
-- when it lapses.
-- ---------------------------------------------------------------------
CREATE TABLE worker_documents
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,

    worker_id            BIGINT       NOT NULL,
    document_id          BIGINT       NOT NULL COMMENT 'the stored file in `documents`',

    document_type        VARCHAR(30)  NOT NULL
        COMMENT 'AADHAAR|PAN|PHOTO|POLICE_VERIFICATION|EDUCATION|EXPERIENCE|MEDICAL|BANK_PROOF|OTHER',

    -- The number printed on the certificate, where it has one. NOT the Aadhaar
    -- or PAN itself: those live encrypted on `workers`, and duplicating them
    -- here in clear would defeat that entirely.
    reference_number     VARCHAR(80)  NULL,

    verification_status  VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING|VERIFIED|REJECTED',
    verified_by_user_id  BIGINT       NULL,
    verified_at          DATETIME(6)  NULL,
    rejection_reason     VARCHAR(500) NULL,

    issued_on            DATE         NULL,
    -- Drives the expiry reminders in Phase 10. A lapsed police verification or
    -- medical certificate is a compliance finding, not an inconvenience.
    expires_on           DATE         NULL,

    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    created_by           VARCHAR(150) NULL,
    updated_by           VARCHAR(150) NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           DATETIME(6)  NULL,
    deleted_by           VARCHAR(150) NULL,

    CONSTRAINT pk_worker_documents PRIMARY KEY (id),
    CONSTRAINT fk_wd_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT fk_wd_document FOREIGN KEY (document_id) REFERENCES documents (id),
    CONSTRAINT fk_wd_verifier FOREIGN KEY (verified_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_wd_worker_type ON worker_documents (worker_id, document_type);
CREATE INDEX ix_wd_verification ON worker_documents (verification_status);
-- The reminder query in Phase 10: what lapses in the next 30 days.
CREATE INDEX ix_wd_expiry ON worker_documents (expires_on);
CREATE INDEX ix_wd_deleted ON worker_documents (deleted);

-- ---------------------------------------------------------------------
-- salary_structures - dated history, exactly like rate_cards
-- ---------------------------------------------------------------------
CREATE TABLE salary_structures
(
    id                 BIGINT         NOT NULL AUTO_INCREMENT,

    worker_id          BIGINT         NOT NULL,

    -- Monthly components. Phase 8's statutory engine reads these; PF is
    -- computed on basic + DA, which is why they are separate columns rather
    -- than one gross figure.
    basic              DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    da                 DECIMAL(12, 2) NOT NULL DEFAULT 0.00 COMMENT 'dearness allowance',
    hra                DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    conveyance         DECIMAL(12, 2) NOT NULL DEFAULT 0.00,
    other_allowance    DECIMAL(12, 2) NOT NULL DEFAULT 0.00,

    ot_rate_per_hour   DECIMAL(10, 2) NULL,

    effective_from     DATE           NOT NULL,
    -- Null means current. Closed by the successor's start date minus one day.
    effective_to       DATE           NULL,

    notes              VARCHAR(500)   NULL,

    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    created_by         VARCHAR(150)   NULL,
    updated_by         VARCHAR(150)   NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    deleted            BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at         DATETIME(6)    NULL,
    deleted_by         VARCHAR(150)   NULL,

    CONSTRAINT pk_salary_structures PRIMARY KEY (id),
    CONSTRAINT fk_ss_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT ck_ss_components CHECK (
        basic >= 0 AND da >= 0 AND hra >= 0 AND conveyance >= 0 AND other_allowance >= 0),
    CONSTRAINT ck_ss_dates CHECK (effective_to IS NULL OR effective_to >= effective_from)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_ss_worker_effective ON salary_structures (worker_id, effective_from);
CREATE INDEX ix_ss_current ON salary_structures (worker_id, effective_to);
CREATE INDEX ix_ss_deleted ON salary_structures (deleted);

-- ---------------------------------------------------------------------
-- deployments - a worker posted to a client site
-- ---------------------------------------------------------------------
CREATE TABLE deployments
(
    id                 BIGINT         NOT NULL AUTO_INCREMENT,

    worker_id          BIGINT         NOT NULL,
    client_id          BIGINT         NOT NULL,
    site_id            BIGINT         NOT NULL,
    -- Which demand this posting fulfils. Nullable because a worker can be
    -- posted directly without a formal requisition.
    requisition_id     BIGINT         NULL,
    category_id        BIGINT         NOT NULL COMMENT 'the role they are deployed as',

    start_date         DATE           NOT NULL,
    -- Null means currently deployed. The overlap rule treats it as open-ended.
    end_date           DATE           NULL,

    shift              VARCHAR(20)    NULL COMMENT 'DAY|NIGHT|ROTATIONAL',

    -- Snapshotted from the rate card applicable on start_date, not read
    -- through to it. A rate card change must not silently restate what an
    -- existing deployment costs, and Phase 9 bills from these.
    monthly_wage       DECIMAL(12, 2) NOT NULL,
    billing_rate       DECIMAL(12, 2) NOT NULL,

    status             VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'ACTIVE|TRANSFERRED|RELEASED|COMPLETED',

    released_on        DATE           NULL,
    release_reason     VARCHAR(500)   NULL,

    notes              VARCHAR(2000)  NULL,

    created_at         DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    created_by         VARCHAR(150)   NULL,
    updated_by         VARCHAR(150)   NULL,
    version            BIGINT         NOT NULL DEFAULT 0,
    deleted            BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at         DATETIME(6)    NULL,
    deleted_by         VARCHAR(150)   NULL,

    CONSTRAINT pk_deployments PRIMARY KEY (id),
    CONSTRAINT fk_deployments_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT fk_deployments_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_deployments_site FOREIGN KEY (site_id) REFERENCES client_sites (id),
    CONSTRAINT fk_deployments_requisition FOREIGN KEY (requisition_id)
        REFERENCES requisitions (id),
    CONSTRAINT fk_deployments_category FOREIGN KEY (category_id)
        REFERENCES manpower_categories (id),
    CONSTRAINT ck_deployments_dates CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_deployments_amounts CHECK (monthly_wage >= 0 AND billing_rate >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The overlap check queries a worker's deployments by date, so this index
-- carries the invariant's weight.
CREATE INDEX ix_deployments_worker_dates ON deployments (worker_id, start_date, end_date);
CREATE INDEX ix_deployments_site_status ON deployments (site_id, status);
CREATE INDEX ix_deployments_client_status ON deployments (client_id, status);
CREATE INDEX ix_deployments_requisition ON deployments (requisition_id);
CREATE INDEX ix_deployments_status ON deployments (status);
CREATE INDEX ix_deployments_deleted ON deployments (deleted);
