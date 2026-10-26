-- =====================================================================
-- V11 - Billing & Invoicing (Phase 9)
--
-- GST invoices, payments, ageing, credit notes.
-- Issued invoice never edited - corrections via credit notes.
-- =====================================================================

CREATE TABLE invoices
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_number    VARCHAR(30)    NOT NULL,
    client_id         BIGINT         NOT NULL,
    payroll_run_id    BIGINT         NULL,
    invoice_date      DATE           NOT NULL,
    due_date          DATE           NOT NULL,
    place_of_supply   VARCHAR(10)    NULL,
    taxable_amount    DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    cgst_amount       DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    sgst_amount       DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    igst_amount       DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    total_amount      DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    amount_paid       DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    status            VARCHAR(20)    NOT NULL DEFAULT 'DRAFT'
        COMMENT 'DRAFT|ISSUED|PARTIALLY_PAID|PAID|CANCELLED',
    notes             VARCHAR(1000)  NULL,

    issued_at         DATETIME(6)    NULL,
    issued_by_id      BIGINT         NULL,
    cancelled_at      DATETIME(6)    NULL,
    cancelled_by_id   BIGINT         NULL,
    cancel_reason     VARCHAR(500)   NULL,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(150)   NULL,
    updated_by        VARCHAR(150)   NULL,
    version           BIGINT         NOT NULL DEFAULT 0,
    deleted           BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at        DATETIME(6)    NULL,
    deleted_by        VARCHAR(150)   NULL,

    CONSTRAINT pk_invoices PRIMARY KEY (id),
    CONSTRAINT uk_invoices_number UNIQUE (invoice_number),
    CONSTRAINT fk_inv_client FOREIGN KEY (client_id) REFERENCES clients (id),
    CONSTRAINT fk_inv_payroll_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs (id),
    CONSTRAINT fk_inv_issued_by FOREIGN KEY (issued_by_id) REFERENCES users (id),
    CONSTRAINT fk_inv_cancelled_by FOREIGN KEY (cancelled_by_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_inv_client_status ON invoices (client_id, status);
CREATE INDEX ix_inv_date ON invoices (invoice_date);
CREATE INDEX ix_inv_due_date ON invoices (due_date);
CREATE INDEX ix_inv_deleted ON invoices (deleted);

CREATE TABLE invoice_lines
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_id        BIGINT         NOT NULL,
    description       VARCHAR(500)   NOT NULL,
    sac_code          VARCHAR(10)    NULL,
    line_type         VARCHAR(20)    NOT NULL
        COMMENT 'WAGES|EMPLOYER_PF|EMPLOYER_ESI|SERVICE_CHARGE|REIMBURSEMENT',
    quantity          DECIMAL(10, 2) NOT NULL DEFAULT 1.00,
    rate              DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    amount            DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    gst_rate          DECIMAL(5, 2)  NOT NULL DEFAULT 18.00,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(150)   NULL,
    updated_by        VARCHAR(150)   NULL,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_invoice_lines PRIMARY KEY (id),
    CONSTRAINT fk_il_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_il_invoice ON invoice_lines (invoice_id);

CREATE TABLE payments
(
    id                BIGINT         NOT NULL AUTO_INCREMENT,
    invoice_id        BIGINT         NOT NULL,
    payment_date      DATE           NOT NULL,
    amount            DECIMAL(14, 2) NOT NULL,
    mode              VARCHAR(10)    NOT NULL COMMENT 'NEFT|RTGS|CHEQUE|UPI|CASH',
    reference_number  VARCHAR(100)   NULL,
    tds_deducted      DECIMAL(14, 2) NOT NULL DEFAULT 0.00,
    remarks           VARCHAR(500)   NULL,

    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    created_by        VARCHAR(150)   NULL,
    updated_by        VARCHAR(150)   NULL,
    version           BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_payments PRIMARY KEY (id),
    CONSTRAINT fk_p_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_p_invoice ON payments (invoice_id);
CREATE INDEX ix_p_date ON payments (payment_date);

CREATE TABLE credit_notes
(
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    credit_note_number  VARCHAR(30)    NOT NULL,
    invoice_id          BIGINT         NOT NULL,
    issue_date          DATE           NOT NULL,
    amount              DECIMAL(14, 2) NOT NULL,
    reason              VARCHAR(500)   NOT NULL,

    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    created_by          VARCHAR(150)   NULL,
    updated_by          VARCHAR(150)   NULL,
    version             BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_credit_notes PRIMARY KEY (id),
    CONSTRAINT uk_cn_number UNIQUE (credit_note_number),
    CONSTRAINT fk_cn_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_cn_invoice ON credit_notes (invoice_id);
