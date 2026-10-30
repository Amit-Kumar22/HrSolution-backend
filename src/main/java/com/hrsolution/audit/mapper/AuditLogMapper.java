package com.hrsolution.audit.mapper;

import com.hrsolution.audit.dto.AuditLogResponse;
import com.hrsolution.audit.entity.AuditLog;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AuditLogMapper {

    @Mapping(target = "reason", expression = "java(auditLog.getReason() != null ? auditLog.getReason() : auditLog.getDescription())")
    AuditLogResponse toResponse(AuditLog auditLog);
}
