package com.hrsolution.audit.mapper;

import com.hrsolution.audit.dto.AuditLogResponse;
import com.hrsolution.audit.entity.AuditLog;
import org.mapstruct.Mapper;

@Mapper
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);
}
