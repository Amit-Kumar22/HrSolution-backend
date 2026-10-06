package com.hrsolution.audit.entity;

import com.hrsolution.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One append-only audit record.
 *
 * <p>Rows are never updated or deleted. Two deliberate denormalisations:
 *
 * <ul>
 *   <li>{@link #userEmail} is stored alongside {@link #userId}, so the trail
 *       stays readable after an account is renamed or deactivated, and does not
 *       depend on a join that might find nothing.</li>
 *   <li>There is <strong>no foreign key</strong> to {@code users}. Audit rows
 *       must outlive the accounts they describe, and a {@code LOGIN_FAILURE}
 *       may reference an email that never had an account at all.</li>
 * </ul>
 *
 * <p>{@link #correlationId} is the same value returned in the
 * {@code X-Correlation-Id} response header and printed on every log line of the
 * originating request - so an audit row, the application log and the error a
 * user reported can be tied together afterwards.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    /** Simple class name of the affected entity, when there is one. */
    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "user_email", length = 180)
    private String userEmail;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "correlation_id", length = 64)
    private String correlationId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues;

    /** False for failed attempts, so successes and failures can be separated. */
    @Column(name = "successful", nullable = false)
    private boolean successful = true;
}
