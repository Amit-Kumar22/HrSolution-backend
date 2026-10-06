package com.hrsolution.audit.dto;

import com.hrsolution.audit.entity.AuditAction;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "One audit trail entry")
public record AuditLogResponse(
        Long id,
        AuditAction action,
        String entityType,
        Long entityId,
        Long userId,
        @Schema(description = "Email of the acting user, stored at the time of the event")
        String userEmail,
        String ipAddress,
        String userAgent,
        @Schema(description = "Ties this entry to the request's log lines and error response")
        String correlationId,
        String description,
        String oldValues,
        String newValues,
        @Schema(description = "False for failed attempts, such as a rejected sign-in")
        boolean successful,
        Instant createdAt) {
}
