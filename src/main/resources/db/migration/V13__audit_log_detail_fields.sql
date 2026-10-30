-- =====================================================================
-- V13 - Clearer audit trail details: reason, solution, clear time
-- =====================================================================

ALTER TABLE audit_logs
    ADD COLUMN reason     VARCHAR(500)   NULL COMMENT 'Human-readable reason for the event',
    ADD COLUMN solution   VARCHAR(500)   NULL COMMENT 'Suggested remediation, where applicable',
    ADD COLUMN clear_time DATETIME(6)    NULL COMMENT 'When the condition clears (e.g. lockout expiry)';

CREATE INDEX ix_audit_logs_clear_time ON audit_logs (clear_time);
