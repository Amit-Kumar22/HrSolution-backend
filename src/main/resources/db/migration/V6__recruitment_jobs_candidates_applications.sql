-- =====================================================================
-- V6 - Recruitment: jobs, candidates, applications, interviews.
--
-- Creates: jobs, candidates, applications, application_status_history,
--          interviews
--
-- This is the hiring funnel that feeds Phase 6's worker records. Four things
-- here carry weight beyond this phase:
--
--  * applications.status is a state machine, not a label. ApplicationStatus
--    rejects illegal jumps (APPLIED -> JOINED, anything -> out of a terminal
--    state), and every accepted move writes a row to
--    application_status_history. Payroll disputes are argued from that trail.
--
--  * A candidate reaching JOINED is what Phase 6 converts into a worker, via
--    workers.candidate_id. Nothing here writes to workers - the table does not
--    exist yet - but JOINED is deliberately a distinct status from SELECTED and
--    OFFERED so the conversion has an unambiguous trigger.
--
--  * Resumes are stored twice on purpose. candidates.resume_document_id is the
--    person's current CV; applications.resume_document_id is the CV actually
--    submitted with that application. Keeping the snapshot means a recruiter
--    reviewing a two-year-old application sees what they were sent then, and it
--    means an anonymous submission can never overwrite a registered
--    candidate's own file.
--
--  * candidates.consent_given records DPDP Act consent. A resume is personal
--    data processed for a stated purpose; without consent we have no lawful
--    basis to keep it. The column is NOT NULL with no default so that every
--    write path has to decide.
--
-- No new permissions: JOB_READ, JOB_MANAGE, CANDIDATE_READ, CANDIDATE_WRITE,
-- APPLICATION_READ, APPLICATION_MANAGE and SELF_APPLICATION_MANAGE were all
-- seeded in V2 and already describe this scope.
--
-- requisitions is NOT created here. The ER diagram groups it with recruitment,
-- but it belongs to the client-demand side and arrives in Phase 6 along with
-- deployment. jobs.requisition_id is therefore added by that migration, not
-- this one - a nullable FK to a table that does not exist is not something
-- Flyway can express.
-- =====================================================================

-- ---------------------------------------------------------------------
-- jobs - an opening advertised on the public site
-- ---------------------------------------------------------------------
CREATE TABLE jobs
(
    id                     BIGINT        NOT NULL AUTO_INCREMENT,

    -- Public URL segment, e.g. /careers/security-guard-pune-plant.
    -- Unique because it addresses the job on the marketing site.
    slug                   VARCHAR(160)  NOT NULL,

    title                  VARCHAR(160)  NOT NULL,
    summary                VARCHAR(400)  NULL COMMENT 'one-line teaser for the job list',
    description            TEXT          NULL COMMENT 'full advert body, rich text',

    category_id            BIGINT        NOT NULL COMMENT 'which kind of worker this hires',

    -- Which client and site the role is for. Both nullable and NEVER exposed
    -- on the public endpoints: clients routinely require that a vacancy not be
    -- attributed to them by name. Staff see it; the internet does not.
    client_id              BIGINT        NULL,
    site_id                BIGINT        NULL,

    -- Where the work is. Free text because a job may cover an area rather than
    -- one address, and because it is advertising copy.
    location               VARCHAR(200)  NULL,
    city                   VARCHAR(100)  NULL,
    state                  VARCHAR(100)  NULL,

    openings               INT           NOT NULL DEFAULT 1,

    -- Monthly rupees. Nullable as a pair: "salary not disclosed" is a normal
    -- advert, and a half-open range is checked in the service rather than here.
    salary_min             DECIMAL(12, 2) NULL,
    salary_max             DECIMAL(12, 2) NULL,

    experience_min_months  INT           NULL,
    experience_max_months  INT           NULL,

    shift                  VARCHAR(20)   NULL COMMENT 'DAY|NIGHT|ROTATIONAL',

    status                 VARCHAR(20)   NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT|OPEN|ON_HOLD|CLOSED|FILLED',

    -- Last day the advert should appear. Expiry is evaluated against this on
    -- read rather than by a scheduled job, so a job never sits visible because
    -- a cron run was missed.
    expires_on             DATE          NULL,

    -- SEO, same shape as service_offerings in V3.
    meta_title             VARCHAR(160)  NULL,
    meta_description       VARCHAR(320)  NULL,

    display_order          INT           NOT NULL DEFAULT 0,

    created_at             DATETIME(6)   NOT NULL,
    updated_at             DATETIME(6)   NOT NULL,
    created_by             VARCHAR(150)  NULL,
    updated_by             VARCHAR(150)  NULL,
    version                BIGINT        NOT NULL DEFAULT 0,
    deleted                BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at             DATETIME(6)   NULL,
    deleted_by             VARCHAR(150)  NULL,

    CONSTRAINT pk_jobs PRIMARY KEY (id),
    CONSTRAINT uk_jobs_slug UNIQUE (slug),
    CONSTRAINT fk_jobs_category FOREIGN KEY (category_id) REFERENCES manpower_categories (id),
    CONSTRAINT fk_jobs_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_jobs_site FOREIGN KEY (site_id) REFERENCES client_sites (id),
    CONSTRAINT ck_jobs_openings CHECK (openings >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- The public list filters on status + expiry and orders by display_order.
CREATE INDEX ix_jobs_status ON jobs (status, expires_on);
CREATE INDEX ix_jobs_category ON jobs (category_id);
CREATE INDEX ix_jobs_client ON jobs (client_id);
CREATE INDEX ix_jobs_deleted ON jobs (deleted);
CREATE INDEX ix_jobs_title ON jobs (title);

-- ---------------------------------------------------------------------
-- candidates - a person in the pipeline, with or without a login
-- ---------------------------------------------------------------------
CREATE TABLE candidates
(
    id                      BIGINT        NOT NULL AUTO_INCREMENT,

    -- Null for an anonymous website application. Set when the person registers
    -- or when staff link an existing CANDIDATE-role login. One login maps to at
    -- most one candidate, hence the unique constraint.
    user_id                 BIGINT        NULL,

    full_name               VARCHAR(160)  NOT NULL,
    -- The identity key for this table. A person who applies to four jobs over
    -- two years must be one candidate row with four applications, not four
    -- candidates, or the recruiter cannot see that they have been seen before.
    email                   VARCHAR(180)  NOT NULL,
    phone                   VARCHAR(20)   NULL,

    city                    VARCHAR(100)  NULL,
    state                   VARCHAR(100)  NULL,

    total_experience_months INT           NULL,
    highest_qualification   VARCHAR(120)  NULL,

    -- Preferred kind of work, used to shortlist against open jobs.
    preferred_category_id   BIGINT        NULL,

    -- The person's CURRENT cv. Distinct from the per-application snapshot; see
    -- the header note.
    resume_document_id      BIGINT        NULL,

    source                  VARCHAR(20)   NOT NULL DEFAULT 'WEBSITE'
        COMMENT 'WEBSITE|REFERRAL|WALK_IN|AGENCY|CAMPUS|OTHER',

    -- DPDP Act. No default: every write path must state it explicitly.
    consent_given           BOOLEAN       NOT NULL,
    consent_given_at        DATETIME(6)   NULL,

    -- Set when a submission trips the honeypot or timing checks. Stored, not
    -- rejected, exactly as for enquiries in V3 - a false positive here loses a
    -- real applicant.
    spam_suspected          BOOLEAN       NOT NULL DEFAULT FALSE,
    spam_reason             VARCHAR(200)  NULL,

    notes                   VARCHAR(2000) NULL COMMENT 'internal, never shown to the candidate',

    created_at              DATETIME(6)   NOT NULL,
    updated_at              DATETIME(6)   NOT NULL,
    created_by              VARCHAR(150)  NULL,
    updated_by              VARCHAR(150)  NULL,
    version                 BIGINT        NOT NULL DEFAULT 0,
    deleted                 BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at              DATETIME(6)   NULL,
    deleted_by              VARCHAR(150)  NULL,

    CONSTRAINT pk_candidates PRIMARY KEY (id),
    CONSTRAINT uk_candidates_email UNIQUE (email),
    CONSTRAINT uk_candidates_user UNIQUE (user_id),
    CONSTRAINT fk_candidates_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_candidates_category FOREIGN KEY (preferred_category_id)
        REFERENCES manpower_categories (id),
    CONSTRAINT fk_candidates_resume FOREIGN KEY (resume_document_id) REFERENCES documents (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_candidates_phone ON candidates (phone);
CREATE INDEX ix_candidates_name ON candidates (full_name);
CREATE INDEX ix_candidates_deleted ON candidates (deleted);
CREATE INDEX ix_candidates_spam ON candidates (spam_suspected);

-- ---------------------------------------------------------------------
-- applications - one candidate against one job
-- ---------------------------------------------------------------------
CREATE TABLE applications
(
    id                   BIGINT        NOT NULL AUTO_INCREMENT,

    job_id               BIGINT        NOT NULL,
    candidate_id         BIGINT        NOT NULL,

    status               VARCHAR(30)   NOT NULL DEFAULT 'APPLIED'
        COMMENT 'APPLIED|SCREENING|SHORTLISTED|INTERVIEW_SCHEDULED|SELECTED|OFFERED|JOINED|REJECTED|ON_HOLD|WITHDRAWN',

    applied_on           DATE          NOT NULL,

    source               VARCHAR(20)   NOT NULL DEFAULT 'WEBSITE'
        COMMENT 'how this application arrived, independent of how the candidate was first met',

    cover_note           VARCHAR(2000) NULL COMMENT 'what the applicant wrote',
    expected_salary      DECIMAL(12, 2) NULL,

    -- The cv submitted with THIS application. Immutable once set.
    resume_document_id   BIGINT        NULL,

    -- Owning recruiter. Null means unassigned, which the pipeline board shows
    -- as a column needing attention.
    assigned_to_user_id  BIGINT        NULL,

    -- Only meaningful in REJECTED. Kept separate from the history note so the
    -- current reason is readable without walking the trail.
    rejection_reason     VARCHAR(500)  NULL,

    created_at           DATETIME(6)   NOT NULL,
    updated_at           DATETIME(6)   NOT NULL,
    created_by           VARCHAR(150)  NULL,
    updated_by           VARCHAR(150)  NULL,
    version              BIGINT        NOT NULL DEFAULT 0,
    deleted              BOOLEAN       NOT NULL DEFAULT FALSE,
    deleted_at           DATETIME(6)   NULL,
    deleted_by           VARCHAR(150)  NULL,

    CONSTRAINT pk_applications PRIMARY KEY (id),
    -- One application per person per job. A second attempt is a 409, not a
    -- duplicate row, so the pipeline count stays truthful.
    --
    -- Note this constraint ignores the soft-delete flag, so a deleted
    -- application would permanently block re-applying. That is why no delete
    -- endpoint is exposed for applications: WITHDRAWN is the terminal state for
    -- an application that should go away, and it keeps the funnel history
    -- honest. The soft-delete columns exist only for consistency with the rest
    -- of the schema.
    CONSTRAINT uk_applications_job_candidate UNIQUE (job_id, candidate_id),
    CONSTRAINT fk_applications_job FOREIGN KEY (job_id) REFERENCES jobs (id),
    CONSTRAINT fk_applications_candidate FOREIGN KEY (candidate_id) REFERENCES candidates (id),
    CONSTRAINT fk_applications_resume FOREIGN KEY (resume_document_id) REFERENCES documents (id),
    CONSTRAINT fk_applications_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_applications_status ON applications (status);
CREATE INDEX ix_applications_job_status ON applications (job_id, status);
CREATE INDEX ix_applications_candidate ON applications (candidate_id);
CREATE INDEX ix_applications_assignee ON applications (assigned_to_user_id);
CREATE INDEX ix_applications_applied_on ON applications (applied_on);
CREATE INDEX ix_applications_deleted ON applications (deleted);

-- ---------------------------------------------------------------------
-- application_status_history - the audit trail for the funnel
--
-- Deliberately append-only and NOT soft-deletable: the whole point is that it
-- cannot be tidied up after the fact. Carries only created_at from the auditing
-- columns because a history row is never updated.
-- ---------------------------------------------------------------------
CREATE TABLE application_status_history
(
    id                  BIGINT       NOT NULL AUTO_INCREMENT,

    application_id      BIGINT       NOT NULL,

    -- Null on the first row, which records the application being created.
    from_status         VARCHAR(30)  NULL,
    to_status           VARCHAR(30)  NOT NULL,

    changed_by_user_id  BIGINT       NULL COMMENT 'null when the candidate acted on their own application',
    changed_by_email    VARCHAR(180) NULL COMMENT 'denormalised so the trail survives a user deletion',

    notes               VARCHAR(1000) NULL,

    created_at          DATETIME(6)  NOT NULL,

    CONSTRAINT pk_application_status_history PRIMARY KEY (id),
    CONSTRAINT fk_ash_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_ash_user FOREIGN KEY (changed_by_user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_ash_application ON application_status_history (application_id, created_at);

-- ---------------------------------------------------------------------
-- interviews
-- ---------------------------------------------------------------------
CREATE TABLE interviews
(
    id                   BIGINT       NOT NULL AUTO_INCREMENT,

    application_id       BIGINT       NOT NULL,

    -- 1, 2, 3... Unique per application so two rows cannot claim to be the
    -- same round.
    round_number         INT          NOT NULL DEFAULT 1,

    scheduled_at         DATETIME(6)  NOT NULL,
    duration_minutes     INT          NULL,

    mode                 VARCHAR(20)  NOT NULL DEFAULT 'IN_PERSON'
        COMMENT 'IN_PERSON|PHONE|VIDEO',

    -- Where to go or which link to join. One column because exactly one of the
    -- two applies and which it is follows from mode.
    venue_or_link        VARCHAR(400) NULL,

    interviewer_user_id  BIGINT       NULL,

    result               VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING|PASS|FAIL|NO_SHOW',

    rating               INT          NULL COMMENT '1-5, interviewer score',
    feedback             VARCHAR(2000) NULL,

    created_at           DATETIME(6)  NOT NULL,
    updated_at           DATETIME(6)  NOT NULL,
    created_by           VARCHAR(150) NULL,
    updated_by           VARCHAR(150) NULL,
    version              BIGINT       NOT NULL DEFAULT 0,
    deleted              BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at           DATETIME(6)  NULL,
    deleted_by           VARCHAR(150) NULL,

    CONSTRAINT pk_interviews PRIMARY KEY (id),
    CONSTRAINT uk_interviews_application_round UNIQUE (application_id, round_number),
    CONSTRAINT fk_interviews_application FOREIGN KEY (application_id) REFERENCES applications (id),
    CONSTRAINT fk_interviews_interviewer FOREIGN KEY (interviewer_user_id) REFERENCES users (id),
    CONSTRAINT ck_interviews_rating CHECK (rating IS NULL OR (rating BETWEEN 1 AND 5)),
    CONSTRAINT ck_interviews_round CHECK (round_number >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_interviews_application ON interviews (application_id);
-- Drives "my interviews this week", the interviewer's home screen.
CREATE INDEX ix_interviews_interviewer_date ON interviews (interviewer_user_id, scheduled_at);
CREATE INDEX ix_interviews_scheduled ON interviews (scheduled_at);
CREATE INDEX ix_interviews_deleted ON interviews (deleted);
