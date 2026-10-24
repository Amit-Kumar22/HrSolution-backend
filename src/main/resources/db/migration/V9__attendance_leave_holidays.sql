-- =====================================================================
-- V9 - Attendance, leave and holidays (Phase 7)
--
-- Supervisor entry, Excel upload, client approval, locking. Once LOCKED
-- a sheet is immutable; editing requires an admin unlock with a recorded
-- reason because payroll has already consumed it.
-- =====================================================================

-- ---------------------------------------------------------------------
-- holidays - national or state-specific
-- ---------------------------------------------------------------------
CREATE TABLE holidays
(
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    name          VARCHAR(150)   NOT NULL,
    holiday_date  DATE           NOT NULL,
    state         VARCHAR(10)    NULL COMMENT 'null means national holiday',
    mandatory     BOOLEAN        NOT NULL DEFAULT TRUE,
    notes         VARCHAR(500)   NULL,

    created_at    DATETIME(6)    NOT NULL,
    updated_at    DATETIME(6)    NOT NULL,
    created_by    VARCHAR(150)   NULL,
    updated_by    VARCHAR(150)   NULL,
    version       BIGINT         NOT NULL DEFAULT 0,
    deleted       BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at    DATETIME(6)    NULL,
    deleted_by    VARCHAR(150)   NULL,

    CONSTRAINT pk_holidays PRIMARY KEY (id),
    CONSTRAINT uk_holidays_date_state UNIQUE (holiday_date, state)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_holidays_date ON holidays (holiday_date);
CREATE INDEX ix_holidays_deleted ON holidays (deleted);

-- ---------------------------------------------------------------------
-- attendance_sheets - one per (site, month, year)
-- ---------------------------------------------------------------------
CREATE TABLE attendance_sheets
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,

    client_id         BIGINT         NOT NULL,
    site_id           BIGINT         NOT NULL,

    month             INT            NOT NULL,
    year              INT            NOT NULL,

    status            VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT|SUBMITTED|CLIENT_APPROVED|LOCKED',

    submitted_at      DATETIME(6)    NULL,
    submitted_by_id   BIGINT         NULL,
    approved_at       DATETIME(6)    NULL,
    approved_by_id    BIGINT         NULL,
    locked_at         DATETIME(6)    NULL,
    locked_by_id      BIGINT         NULL,
    unlock_reason     VARCHAR(1000)  NULL,
    unlocked_at       DATETIME(6)    NULL,
    unlocked_by_id    BIGINT         NULL,

    remarks           VARCHAR(1000)  NULL,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(150)   NULL,
    updated_by        VARCHAR(150)   NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at        DATETIME(6)    NULL,
    deleted_by        VARCHAR(150)   NULL,

    CONSTRAINT pk_attendance_sheets PRIMARY KEY (id),
    CONSTRAINT fk_as_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_as_site FOREIGN KEY (site_id) REFERENCES client_sites (id),
    CONSTRAINT fk_as_submitted_by FOREIGN KEY (submitted_by_id) REFERENCES users (id),
    CONSTRAINT fk_as_approved_by FOREIGN KEY (approved_by_id) REFERENCES users (id),
    CONSTRAINT fk_as_locked_by FOREIGN KEY (locked_by_id) REFERENCES users (id),
    CONSTRAINT fk_as_unlocked_by FOREIGN KEY (unlocked_by_id) REFERENCES users (id),
    CONSTRAINT uk_as_site_month_year UNIQUE (site_id, month, year),
    CONSTRAINT ck_as_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_as_year CHECK (year BETWEEN 2000 AND 2100)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_as_client_status ON attendance_sheets (client_id, status);
CREATE INDEX ix_as_site_status ON attendance_sheets (site_id, status);
CREATE INDEX ix_as_period ON attendance_sheets (year, month);
CREATE INDEX ix_as_deleted ON attendance_sheets (deleted);

-- ---------------------------------------------------------------------
-- attendance_entries - per worker per day
-- ---------------------------------------------------------------------
CREATE TABLE attendance_entries
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,

    sheet_id          BIGINT         NOT NULL,
    worker_id         BIGINT         NOT NULL,
    deployment_id     BIGINT         NOT NULL,

    attendance_date   DATE           NOT NULL,

    status            VARCHAR(20)    NOT NULL DEFAULT 'PRESENT'
        COMMENT 'PRESENT|ABSENT|HALF_DAY|WEEK_OFF|HOLIDAY|LEAVE',

    overtime_hours    DECIMAL(4, 2)  NOT NULL DEFAULT 0.00,
    remarks           VARCHAR(500)   NULL,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(150)   NULL,
    updated_by        VARCHAR(150)   NULL,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_attendance_entries PRIMARY KEY (id),
    CONSTRAINT fk_ae_sheet FOREIGN KEY (sheet_id) REFERENCES attendance_sheets (id),
    CONSTRAINT fk_ae_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT fk_ae_deployment FOREIGN KEY (deployment_id) REFERENCES deployments (id),
    CONSTRAINT uk_ae_sheet_worker_date UNIQUE (sheet_id, worker_id, attendance_date),
    CONSTRAINT ck_ae_overtime CHECK (overtime_hours >= 0),
    CONSTRAINT ck_ae_hours_reasonable CHECK (overtime_hours <= 24)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_ae_sheet ON attendance_entries (sheet_id);
CREATE INDEX ix_ae_worker_date ON attendance_entries (worker_id, attendance_date);
CREATE INDEX ix_ae_deployment ON attendance_entries (deployment_id);
CREATE INDEX ix_ae_date ON attendance_entries (attendance_date);

-- ---------------------------------------------------------------------
-- leave_requests
-- ---------------------------------------------------------------------
CREATE TABLE leave_requests
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,

    worker_id         BIGINT         NOT NULL,

    from_date         DATE           NOT NULL,
    to_date           DATE           NOT NULL,

    leave_type        VARCHAR(20)    NOT NULL COMMENT 'CASUAL|SICK|EARNED|UNPAID',
    status            VARCHAR(20)    NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING|APPROVED|REJECTED|CANCELLED',

    reason            VARCHAR(1000)  NOT NULL,
    remarks           VARCHAR(1000)  NULL,

    approved_by_user_id BIGINT       NULL,
    approved_at       DATETIME(6)    NULL,
    rejection_reason  VARCHAR(500)   NULL,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(150)   NULL,
    updated_by        VARCHAR(150)   NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at        DATETIME(6)    NULL,
    deleted_by        VARCHAR(150)   NULL,

    CONSTRAINT pk_leave_requests PRIMARY KEY (id),
    CONSTRAINT fk_lr_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT fk_lr_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_lr_dates CHECK (to_date >= from_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_lr_worker ON leave_requests (worker_id, status);
CREATE INDEX ix_lr_dates ON leave_requests (from_date, to_date);
CREATE INDEX ix_lr_status ON leave_requests (status);
CREATE INDEX ix_lr_deleted ON leave_requests (deleted);
