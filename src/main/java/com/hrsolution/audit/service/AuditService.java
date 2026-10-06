package com.hrsolution.audit.service;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.entity.AuditLog;
import com.hrsolution.audit.repository.AuditLogRepository;
import com.hrsolution.common.web.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail.
 *
 * <p>Two decisions worth understanding before changing anything here.
 *
 * <p><strong>Each write is {@link Propagation#REQUIRES_NEW}.</strong> Audit rows
 * commit in their own transaction, independent of the caller's. This is
 * essential rather than incidental: a failed login, a rejected registration and
 * a detected token reuse all end by throwing, which rolls the caller's
 * transaction back. Joining that transaction would roll the audit row back
 * too - so the security events most worth recording would be precisely the ones
 * that never got recorded.
 *
 * <p><strong>Writes are synchronous.</strong> Making them {@code @Async} would
 * avoid a round trip on the login path, but would also mean losing the tail of
 * the audit trail whenever the process is killed - exactly when an incident is
 * most likely to be under way. One insert against an indexed table is a price
 * worth paying for a trail that is actually complete.
 *
 * <p>Failures are swallowed and logged. An audit insert that fails must not
 * turn a successful login into a 500: the user's action already succeeded, and
 * the gap is visible in the application log.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Records a security event - login, logout, token rotation, password change,
     * lockout, reuse detection.
     *
     * @param userEmail stored even when {@code userId} is null, because a
     *                  failed login may name an email that has no account, and
     *                  that attempt is worth keeping
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(AuditAction action,
                                    Long userId,
                                    String userEmail,
                                    boolean successful,
                                    String description,
                                    RequestContext context) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setUserId(userId);
        entry.setUserEmail(userEmail);
        entry.setSuccessful(successful);
        entry.setDescription(truncate(description));
        applyContext(entry, context);
        save(entry);
    }

    /**
     * Records a change to a business record. {@code oldValues}/{@code newValues}
     * are JSON strings; used from Phase 4 onward.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEntityChange(AuditAction action,
                                   String entityType,
                                   Long entityId,
                                   Long userId,
                                   String userEmail,
                                   String description,
                                   String oldValues,
                                   String newValues,
                                   RequestContext context) {
        AuditLog entry = new AuditLog();
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setUserId(userId);
        entry.setUserEmail(userEmail);
        entry.setSuccessful(true);
        entry.setDescription(truncate(description));
        entry.setOldValues(oldValues);
        entry.setNewValues(newValues);
        applyContext(entry, context);
        save(entry);
    }

    private void applyContext(AuditLog entry, RequestContext context) {
        if (context == null) {
            return;
        }
        entry.setIpAddress(context.ipAddress());
        entry.setUserAgent(context.userAgent());
        entry.setCorrelationId(context.correlationId());
    }

    private void save(AuditLog entry) {
        try {
            auditLogRepository.save(entry);
        } catch (RuntimeException e) {
            // Never let an audit failure break the operation being audited.
            log.error("Failed to write audit entry action={} user={}",
                    entry.getAction(), entry.getUserEmail(), e);
        }
    }

    /** The column holds 500 characters; a longer description is clipped, not rejected. */
    private String truncate(String description) {
        if (description == null) {
            return null;
        }
        return description.length() <= 500 ? description : description.substring(0, 500);
    }
}
