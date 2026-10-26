-- =====================================================================
-- V12 - Compliance, reminders, notifications, dashboards, reports, audit viewer (Phase 10)
-- =====================================================================

CREATE TABLE licences
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    licence_type    VARCHAR(30)    NOT NULL COMMENT 'CONTRACT_LABOUR|PSARA|GST|PF|ESI|SHOP_ESTABLISHMENT|TRADE|ISO',
    licence_number  VARCHAR(100)   NOT NULL,
    client_id       BIGINT         NULL COMMENT 'contract labour licences are per establishment',
    issue_date      DATE           NULL,
    expiry_date     DATE           NULL COMMENT 'reminders at 60/30/7 days',
    document_path   VARCHAR(500)   NULL,
    status          VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE|EXPIRED|UNDER_RENEWAL',
    issuing_authority VARCHAR(200)  NULL,
    notes           VARCHAR(1000)  NULL,

    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    created_by      VARCHAR(150)   NULL,
    updated_by      VARCHAR(150)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    deleted         BOOLEAN        NOT NULL DEFAULT FALSE,
    deleted_at      DATETIME(6)    NULL,
    deleted_by      VARCHAR(150)   NULL,

    CONSTRAINT pk_licences PRIMARY KEY (id),
    CONSTRAINT fk_lic_client FOREIGN KEY (client_id) REFERENCES clients (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_lic_expiry ON licences (expiry_date);
CREATE INDEX ix_lic_type_status ON licences (licence_type, status);
CREATE INDEX ix_lic_deleted ON licences (deleted);

CREATE TABLE statutory_challans
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    challan_type    VARCHAR(10)    NOT NULL COMMENT 'PF|ESI|PT|LWF|TDS',
    month           INT            NOT NULL,
    year            INT            NOT NULL,
    amount          DECIMAL(14, 2) NOT NULL,
    paid_on         DATE           NULL,
    challan_number  VARCHAR(100)   NULL,
    document_path   VARCHAR(500)   NULL,
    return_filed    BOOLEAN        NOT NULL DEFAULT FALSE,
    notes           VARCHAR(1000)  NULL,

    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    created_by      VARCHAR(150)   NULL,
    updated_by      VARCHAR(150)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_statutory_challans PRIMARY KEY (id),
    CONSTRAINT uk_sc_type_month_year UNIQUE (challan_type, month, year)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_sc_period ON statutory_challans (year, month);

CREATE TABLE notifications
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    user_id         BIGINT         NOT NULL,
    title           VARCHAR(200)   NOT NULL,
    message         VARCHAR(1000)  NOT NULL,
    type            VARCHAR(20)    NOT NULL DEFAULT 'INFO' COMMENT 'INFO|WARNING|ACTION_REQUIRED',
    link            VARCHAR(500)   NULL,
    read_flag       BOOLEAN        NOT NULL DEFAULT FALSE,
    read_at         DATETIME(6)    NULL,

    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    created_by      VARCHAR(150)   NULL,
    updated_by      VARCHAR(150)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_n_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_n_user_read ON notifications (user_id, read_flag);
CREATE INDEX ix_n_created ON notifications (created_at);
