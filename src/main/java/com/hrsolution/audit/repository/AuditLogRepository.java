package com.hrsolution.audit.repository;

import com.hrsolution.audit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * Append-only. Nothing here updates or deletes a row, and the audit viewer
 * reads through {@code JpaSpecificationExecutor} so it can be filtered by
 * action, user and date range.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>,
        JpaSpecificationExecutor<AuditLog> {
}
