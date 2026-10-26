-- =====================================================================
-- V10 - Payroll (Phase 8)
--
-- Configurable statutory engine, payslip PDF, bank/PF/ESI exports.
-- payroll_item_components stores rate and base for every figure.
-- =====================================================================

CREATE TABLE payroll_runs
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    client_id           BIGINT         NULL COMMENT 'null = all clients',
    month               INT            NOT NULL,
    year                INT            NOT NULL,

    status              VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT|PROCESSED|APPROVED|PAID|LOCKED',

    total_gross         DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    total_deductions    DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    total_net           DECIMAL(14, 2) NOT NULL DEFAULT 0.00,

    processed_at        DATETIME(6)    NULL,
    processed_by_id     BIGINT         NULL,
    approved_by_user_id BIGINT         NULL,
    approved_at         DATETIME(6)    NULL,
    paid_at             DATETIME(6)    NULL,
    locked_at           DATETIME(6)    NULL,
    notes               VARCHAR(1000)  NULL,

    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(150)   NULL,
    updated_by          VARCHAR(150)   NULL,
    version             BIGINT         NOT NULL DEFAULT 0,
    deleted             BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at          DATETIME(6)    NULL,
    deleted_by          VARCHAR(150)   NULL,

    CONSTRAINT pk_payroll_runs PRIMARY KEY (id),
    CONSTRAINT fk_pr_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_pr_processed_by FOREIGN KEY (processed_by_id) REFERENCES users (id),
    CONSTRAINT fk_pr_approved_by FOREIGN KEY (approved_by_user_id) REFERENCES users (id),
    CONSTRAINT ck_pr_month CHECK (month BETWEEN 1 AND 12),
    CONSTRAINT ck_pr_year CHECK (year BETWEEN 2000 AND 2100)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE UNIQUE INDEX uk_pr_client_month_year ON payroll_runs (client_id, month, year);
CREATE INDEX ix_pr_status ON payroll_runs (status);
CREATE INDEX ix_pr_period ON payroll_runs (year, month);
CREATE INDEX ix_pr_deleted ON payroll_runs (deleted);

CREATE TABLE payroll_items
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    payroll_run_id      BIGINT         NOT NULL,
    worker_id           BIGINT         NOT NULL,
    deployment_id       BIGINT         NOT NULL,
    attendance_sheet_id BIGINT         NULL COMMENT 'the locked source',

    paid_days           INT            NOT NULL DEFAULT 0,
    total_days          INT            NOT NULL DEFAULT 0,
    overtime_hours      DECIMAL(6, 2)  NOT NULL DEFAULT 0.00,

    gross_earnings      DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    total_deductions    DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    net_pay             DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    below_minimum_wage  BOOLEAN        NOT NULL DEFAULT FALSE,

    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(150)   NULL,
    updated_by          VARCHAR(150)   NULL,
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_payroll_items PRIMARY KEY (id),
    CONSTRAINT fk_pi_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs (id),
    CONSTRAINT fk_pi_worker FOREIGN KEY (worker_id) REFERENCES workers (id),
    CONSTRAINT fk_pi_deployment FOREIGN KEY (deployment_id) REFERENCES deployments (id),
    CONSTRAINT fk_pi_sheet FOREIGN KEY (attendance_sheet_id) REFERENCES attendance_sheets (id),
    CONSTRAINT uk_pi_run_worker_deployment UNIQUE (payroll_run_id, worker_id, deployment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pi_run ON payroll_items (payroll_run_id);
CREATE INDEX ix_pi_worker ON payroll_items (worker_id);
CREATE INDEX ix_pi_deployment ON payroll_items (deployment_id);

CREATE TABLE payroll_item_components
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    payroll_item_id     BIGINT         NOT NULL,
    component_type      VARCHAR(20)    NOT NULL COMMENT 'EARNING|DEDUCTION|EMPLOYER_CONTRIBUTION',
    code                VARCHAR(20)    NOT NULL COMMENT 'BASIC|DA|HRA|OT|PF_EE|ESI_EE|PT|ADVANCE|PF_ER|ESI_ER',
    amount              DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    computed_on         DECIMAL(14, 2) NULL COMMENT 'the base the rate was applied to',
    rate_percent        DECIMAL(6, 3)  NULL COMMENT 'the rate used, captured for audit',

    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(150)   NULL,
    updated_by          VARCHAR(150)   NULL,
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_payroll_item_components PRIMARY KEY (id),
    CONSTRAINT fk_pic_item FOREIGN KEY (payroll_item_id) REFERENCES payroll_items (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_pic_item ON payroll_item_components (payroll_item_id);
CREATE INDEX ix_pic_code ON payroll_item_components (code);
