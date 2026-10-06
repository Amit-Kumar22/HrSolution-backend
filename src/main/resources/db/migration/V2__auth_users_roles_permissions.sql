-- =====================================================================
-- V2 - Authentication, RBAC and the security audit trail.
--
-- Creates: users, roles, permissions, role_permissions, user_roles,
--          refresh_tokens, verification_tokens, password_reset_tokens,
--          audit_logs
--
-- Seeds the nine roles, the permission catalogue and the role->permission
-- matrix. User accounts are NOT seeded here: a BCrypt hash cannot be computed
-- in SQL, so the SUPER_ADMIN is created at startup by SuperAdminSeeder from
-- SUPER_ADMIN_EMAIL / SUPER_ADMIN_PASSWORD, and demo accounts by DemoDataSeeder
-- under the local profile only.
--
-- Authorization is checked on PERMISSION names, never role names, so the
-- matrix at the bottom of this file can be re-pointed from the admin API
-- without touching application code.
-- =====================================================================

-- ---------------------------------------------------------------------
-- users
-- ---------------------------------------------------------------------
CREATE TABLE users
(
    id              BIGINT       NOT NULL AUTO_INCREMENT,

    email           VARCHAR(180) NOT NULL,
    password_hash   VARCHAR(100) NOT NULL COMMENT 'BCrypt, strength 12',
    first_name      VARCHAR(100) NOT NULL,
    last_name       VARCHAR(100) NULL,
    phone           VARCHAR(20)  NULL,

    status          VARCHAR(30)  NOT NULL COMMENT 'PENDING_VERIFICATION|PENDING_APPROVAL|ACTIVE|DISABLED|REJECTED',
    email_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    email_verified_at DATETIME(6) NULL,

    -- Lockout state. 5 failures locks the account for 15 minutes; both columns
    -- reset on a successful login.
    failed_attempts INT          NOT NULL DEFAULT 0,
    locked_until    DATETIME(6)  NULL,

    -- Incrementing this invalidates every access token already issued to the
    -- user, without needing a token blacklist. Bumped on password change,
    -- role change and account disable.
    token_version   INT          NOT NULL DEFAULT 0,

    last_login_at   DATETIME(6)  NULL,
    last_login_ip   VARCHAR(45)  NULL COMMENT 'sized for IPv6',

    -- Consent captured at registration, per the DPDP Act.
    consent_given_at DATETIME(6) NULL,

    -- Company name a client typed when self-registering. Holds the self-declared
    -- value until an admin approves the account, at which point Phase 4 creates
    -- the real clients row and this becomes redundant history.
    pending_company_name VARCHAR(200) NULL,

    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    created_by      VARCHAR(150) NULL,
    updated_by      VARCHAR(150) NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    deleted_at      DATETIME(6)  NULL,
    deleted_by      VARCHAR(150) NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    -- Unique across soft-deleted rows too, deliberately: re-using the email of
    -- a deactivated account would silently attach the old audit trail to a new
    -- person. Deleted accounts must be restored, not recreated.
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_users_status ON users (status);
CREATE INDEX ix_users_deleted ON users (deleted);

-- ---------------------------------------------------------------------
-- roles / permissions
-- ---------------------------------------------------------------------
CREATE TABLE roles
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50)  NOT NULL,
    description VARCHAR(255) NULL,
    -- System roles are referenced by application logic and cannot be deleted
    -- or renamed through the admin API.
    system_role BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(150) NULL,
    updated_by  VARCHAR(150) NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE permissions
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(60)  NOT NULL,
    module      VARCHAR(40)  NOT NULL COMMENT 'grouping for the admin UI',
    description VARCHAR(255) NULL,

    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    created_by  VARCHAR(150) NULL,
    updated_by  VARCHAR(150) NULL,
    version     BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uk_permissions_name UNIQUE (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_permissions_module ON permissions (module);

CREATE TABLE role_permissions
(
    role_id       BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,

    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_roles
(
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,

    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- refresh_tokens
--
-- One row per issued refresh token, forming a rotation chain. On refresh the
-- presented token is revoked and a successor is created in the same family.
-- Presenting an already-revoked token means it leaked, so the whole family is
-- revoked and the user must log in again.
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens
(
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    user_id               BIGINT       NOT NULL,

    -- SHA-256 of the opaque token, hex encoded. The plaintext value exists only
    -- in the client's cookie; a database leak therefore yields no usable token.
    token_hash            CHAR(64)     NOT NULL,
    -- Shared by every token in one login's rotation chain.
    family_id             CHAR(36)     NOT NULL,
    replaced_by_token_id  BIGINT       NULL,

    expires_at            DATETIME(6)  NOT NULL,
    revoked_at            DATETIME(6)  NULL,
    revoked_reason        VARCHAR(50)  NULL COMMENT 'ROTATED|LOGOUT|LOGOUT_ALL|REUSE_DETECTED|PASSWORD_CHANGED|ADMIN_REVOKED|SESSION_REVOKED',
    remember_me           BOOLEAN      NOT NULL DEFAULT FALSE,

    ip_address            VARCHAR(45)  NULL,
    user_agent            VARCHAR(300) NULL COMMENT 'shown on the active sessions screen',

    created_at            DATETIME(6)  NOT NULL,
    updated_at            DATETIME(6)  NOT NULL,
    created_by            VARCHAR(150) NULL,
    updated_by            VARCHAR(150) NULL,
    version               BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uk_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_refresh_tokens_replaced_by FOREIGN KEY (replaced_by_token_id) REFERENCES refresh_tokens (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
-- Supports the scheduled purge of expired rows.
CREATE INDEX ix_refresh_tokens_expires_at ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------
-- verification_tokens / password_reset_tokens
--
-- Both single-use and hashed, for the same reason as refresh tokens: a leaked
-- database must not hand over working account-recovery links.
-- ---------------------------------------------------------------------
CREATE TABLE verification_tokens
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash CHAR(64)     NOT NULL,
    expires_at DATETIME(6)  NOT NULL COMMENT '24 hours',
    used_at    DATETIME(6)  NULL COMMENT 'non-null means already consumed',

    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    created_by VARCHAR(150) NULL,
    updated_by VARCHAR(150) NULL,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_verification_tokens PRIMARY KEY (id),
    CONSTRAINT uk_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_verification_tokens_user ON verification_tokens (user_id);
CREATE INDEX ix_verification_tokens_expires_at ON verification_tokens (expires_at);

CREATE TABLE password_reset_tokens
(
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash CHAR(64)     NOT NULL,
    expires_at DATETIME(6)  NOT NULL COMMENT '30 minutes',
    used_at    DATETIME(6)  NULL,
    ip_address VARCHAR(45)  NULL COMMENT 'who requested the reset',

    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    created_by VARCHAR(150) NULL,
    updated_by VARCHAR(150) NULL,
    version    BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT pk_password_reset_tokens PRIMARY KEY (id),
    CONSTRAINT uk_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_password_reset_tokens_user ON password_reset_tokens (user_id);
CREATE INDEX ix_password_reset_tokens_expires_at ON password_reset_tokens (expires_at);

-- ---------------------------------------------------------------------
-- audit_logs
--
-- Security events from this phase; entity change tracking joins it from
-- Phase 4 onward. correlation_id matches the X-Correlation-Id on the response
-- and every log line of the originating request, so an audit row, the
-- application log and the error a user reported can be joined after the fact.
-- ---------------------------------------------------------------------
CREATE TABLE audit_logs
(
    id             BIGINT        NOT NULL AUTO_INCREMENT,

    action         VARCHAR(50)   NOT NULL,
    entity_type    VARCHAR(60)   NULL,
    entity_id      BIGINT        NULL,

    -- The acting user's id AND email are both stored. The email is denormalised
    -- on purpose: an audit trail has to stay readable after the account is
    -- renamed or deactivated, and must not depend on a join that may fail.
    user_id        BIGINT        NULL,
    user_email     VARCHAR(180)  NULL,

    ip_address     VARCHAR(45)   NULL,
    user_agent     VARCHAR(300)  NULL,
    correlation_id VARCHAR(64)   NULL,

    description    VARCHAR(500)  NULL,
    old_values     TEXT          NULL COMMENT 'JSON',
    new_values     TEXT          NULL COMMENT 'JSON',
    successful     BOOLEAN       NOT NULL DEFAULT TRUE,

    created_at     DATETIME(6)   NOT NULL,
    updated_at     DATETIME(6)   NOT NULL,
    created_by     VARCHAR(150)  NULL,
    updated_by     VARCHAR(150)  NULL,
    version        BIGINT        NOT NULL DEFAULT 0,

    CONSTRAINT pk_audit_logs PRIMARY KEY (id)
    -- Deliberately no FK to users: audit rows must outlive the accounts they
    -- describe, and a login FAILURE may reference an email with no account.
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_audit_logs_action ON audit_logs (action);
CREATE INDEX ix_audit_logs_user ON audit_logs (user_id);
CREATE INDEX ix_audit_logs_created_at ON audit_logs (created_at);
CREATE INDEX ix_audit_logs_entity ON audit_logs (entity_type, entity_id);

-- =====================================================================
-- Seed data: roles
-- =====================================================================
INSERT INTO roles (name, description, system_role, created_at, updated_at, created_by, updated_by, version)
VALUES ('SUPER_ADMIN', 'Company owner. Full access including system settings and role management.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('ADMIN', 'Operations head. All modules except system settings.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('HR_RECRUITER', 'Recruitment team. Jobs, candidates, applications, interviews, worker onboarding.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('OPERATIONS_MANAGER', 'Deployment team. Requisitions, deployments, sites, attendance approval.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('SITE_SUPERVISOR', 'Field supervisor. Marks attendance for assigned sites only.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('ACCOUNTS', 'Finance team. Payroll, invoices, payments, statutory reports.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('CLIENT', 'Client company user. Own requisitions, deployed workers, attendance approval, invoices.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('WORKER', 'Deployed employee. Own profile, documents, attendance, payslips, leave.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
       ('CANDIDATE', 'Job seeker. Own profile, resume, applications.', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0);

-- =====================================================================
-- Seed data: permissions
--
-- Names must stay in step with the constants in
-- common/security/Permissions.java. PermissionCatalogueIT asserts that the two
-- match, so a typo here fails the build rather than silently granting nothing.
-- =====================================================================
INSERT INTO permissions (name, module, description, created_at, updated_at, created_by, updated_by, version)
VALUES
    -- User and access administration
    ('USER_READ', 'USER', 'View user accounts', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('USER_MANAGE', 'USER', 'Create, edit, enable and disable user accounts', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('ROLE_MANAGE', 'USER', 'Assign roles to users and edit role permissions', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Clients
    ('CLIENT_READ', 'CLIENT', 'View clients, sites and contracts', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('CLIENT_WRITE', 'CLIENT', 'Create and edit clients, sites, contracts and rate cards', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('CLIENT_APPROVE', 'CLIENT', 'Approve or reject client registrations', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Requisitions
    ('REQUISITION_READ', 'REQUISITION', 'View manpower requisitions', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('REQUISITION_CREATE', 'REQUISITION', 'Raise a manpower requisition', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('REQUISITION_APPROVE', 'REQUISITION', 'Approve or reject requisitions', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Recruitment
    ('JOB_READ', 'RECRUITMENT', 'View job postings', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('JOB_MANAGE', 'RECRUITMENT', 'Create, publish and expire job postings', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('CANDIDATE_READ', 'RECRUITMENT', 'View candidate profiles', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('CANDIDATE_WRITE', 'RECRUITMENT', 'Create and edit candidate profiles', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('APPLICATION_READ', 'RECRUITMENT', 'View applications', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('APPLICATION_MANAGE', 'RECRUITMENT', 'Move applications through the pipeline and schedule interviews', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Workers
    ('WORKER_READ', 'WORKER', 'View worker records with sensitive fields masked', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('WORKER_WRITE', 'WORKER', 'Create and edit worker records, verify documents', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('WORKER_SENSITIVE_READ', 'WORKER', 'View unmasked Aadhaar, PAN and bank account numbers', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Deployment
    ('DEPLOYMENT_READ', 'DEPLOYMENT', 'View deployments', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('DEPLOYMENT_MANAGE', 'DEPLOYMENT', 'Deploy, transfer and release workers', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Attendance
    ('ATTENDANCE_READ', 'ATTENDANCE', 'View attendance sheets', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('ATTENDANCE_MARK', 'ATTENDANCE', 'Mark daily attendance and upload monthly sheets', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('ATTENDANCE_APPROVE', 'ATTENDANCE', 'Approve, lock and unlock attendance sheets', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('LEAVE_APPROVE', 'ATTENDANCE', 'Approve or reject leave requests', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Payroll
    ('PAYROLL_READ', 'PAYROLL', 'View payroll runs and payslips', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('PAYROLL_PROCESS', 'PAYROLL', 'Process, approve and mark payroll as paid', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Billing
    ('INVOICE_READ', 'BILLING', 'View invoices', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('INVOICE_MANAGE', 'BILLING', 'Generate, issue and cancel invoices and credit notes', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('PAYMENT_MANAGE', 'BILLING', 'Record payments received against invoices', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Compliance
    ('COMPLIANCE_READ', 'COMPLIANCE', 'View licences and statutory challans', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('COMPLIANCE_MANAGE', 'COMPLIANCE', 'Manage licences, challans and the statutory checklist', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Cross-cutting
    ('REPORT_VIEW', 'REPORT', 'View and export reports', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('ENQUIRY_MANAGE', 'ENQUIRY', 'View and action website enquiries and contact messages', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('SETTINGS_MANAGE', 'SETTINGS', 'Edit company profile, statutory configuration and number series', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('AUDIT_VIEW', 'AUDIT', 'View the audit log', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),

    -- Self-service. Ownership is additionally enforced in the services, so
    -- holding these never exposes another person's records.
    ('SELF_PROFILE_MANAGE', 'SELF', 'View and edit own profile and documents', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('SELF_ATTENDANCE_READ', 'SELF', 'View own attendance', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('SELF_PAYSLIP_READ', 'SELF', 'View and download own payslips', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('SELF_LEAVE_MANAGE', 'SELF', 'Raise and cancel own leave requests', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0),
    ('SELF_APPLICATION_MANAGE', 'SELF', 'Apply for jobs and track own applications', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 'flyway', 'flyway', 0);

-- =====================================================================
-- Seed data: role -> permission matrix
-- Documented in full in docs/rbac.md.
-- =====================================================================

-- SUPER_ADMIN: everything.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'SUPER_ADMIN';

-- ADMIN: everything except system settings and role administration.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ADMIN'
  AND p.name NOT IN ('SETTINGS_MANAGE', 'ROLE_MANAGE')
  AND p.module <> 'SELF';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'HR_RECRUITER'
  AND p.name IN ('JOB_READ', 'JOB_MANAGE', 'CANDIDATE_READ', 'CANDIDATE_WRITE',
                 'APPLICATION_READ', 'APPLICATION_MANAGE',
                 'WORKER_READ', 'WORKER_WRITE',
                 'REQUISITION_READ', 'CLIENT_READ', 'REPORT_VIEW');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'OPERATIONS_MANAGER'
  AND p.name IN ('REQUISITION_READ', 'REQUISITION_CREATE', 'REQUISITION_APPROVE',
                 'DEPLOYMENT_READ', 'DEPLOYMENT_MANAGE',
                 'WORKER_READ', 'WORKER_WRITE',
                 'ATTENDANCE_READ', 'ATTENDANCE_MARK', 'ATTENDANCE_APPROVE', 'LEAVE_APPROVE',
                 'CLIENT_READ', 'CLIENT_WRITE', 'REPORT_VIEW');

-- SITE_SUPERVISOR: marking attendance only, and only for assigned sites -
-- the site restriction is an ownership check in the service, not a permission.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'SITE_SUPERVISOR'
  AND p.name IN ('ATTENDANCE_READ', 'ATTENDANCE_MARK',
                 'WORKER_READ', 'DEPLOYMENT_READ', 'SELF_PROFILE_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'ACCOUNTS'
  AND p.name IN ('PAYROLL_READ', 'PAYROLL_PROCESS',
                 'INVOICE_READ', 'INVOICE_MANAGE', 'PAYMENT_MANAGE',
                 'COMPLIANCE_READ', 'COMPLIANCE_MANAGE',
                 'WORKER_READ', 'WORKER_SENSITIVE_READ',
                 'CLIENT_READ', 'ATTENDANCE_READ', 'REPORT_VIEW');

-- CLIENT: own data only. Every one of these is additionally scoped to the
-- caller's own client_id by an ownership check in the service layer.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'CLIENT'
  AND p.name IN ('REQUISITION_READ', 'REQUISITION_CREATE',
                 'DEPLOYMENT_READ', 'WORKER_READ',
                 'ATTENDANCE_READ', 'ATTENDANCE_APPROVE',
                 'INVOICE_READ', 'SELF_PROFILE_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'WORKER'
  AND p.name IN ('SELF_PROFILE_MANAGE', 'SELF_ATTENDANCE_READ',
                 'SELF_PAYSLIP_READ', 'SELF_LEAVE_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
         CROSS JOIN permissions p
WHERE r.name = 'CANDIDATE'
  AND p.name IN ('SELF_PROFILE_MANAGE', 'SELF_APPLICATION_MANAGE', 'JOB_READ');
