package com.hrsolution.audit.service;

import com.hrsolution.audit.dto.AuditLogResponse;
import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.entity.AuditLog;
import com.hrsolution.audit.mapper.AuditLogMapper;
import com.hrsolution.audit.repository.AuditLogRepository;
import com.hrsolution.common.repository.SpecificationUtils;
import com.hrsolution.common.web.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Read side of the audit trail, for the audit viewer.
 *
 * <p>Read-only by design: there is no method here that writes, updates or
 * deletes. An audit trail an administrator can edit is not an audit trail.
 * Writes go through {@link AuditService}, which only ever inserts.
 */
@Service
@RequiredArgsConstructor
public class AuditQueryService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper auditLogMapper;

    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(AuditAction action,
                                                 Long userId,
                                                 String userEmail,
                                                 String entityType,
                                                 Boolean successful,
                                                 Instant from,
                                                 Instant to,
                                                 Pageable pageable) {
        Specification<AuditLog> specification = Specification.<AuditLog>unrestricted()
                .and(SpecificationUtils.equal("action", action))
                .and(SpecificationUtils.equal("userId", userId))
                .and(SpecificationUtils.containsIgnoreCase("userEmail", userEmail))
                .and(SpecificationUtils.equal("entityType", entityType))
                .and(SpecificationUtils.equal("successful", successful))
                .and(SpecificationUtils.between("createdAt", from, to));

        Page<AuditLog> page = auditLogRepository.findAll(specification, pageable);
        return PageResponse.from(page, auditLogMapper::toResponse);
    }
}
