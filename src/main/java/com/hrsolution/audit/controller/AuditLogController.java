package com.hrsolution.audit.controller;

import com.hrsolution.audit.dto.AuditLogResponse;
import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditQueryService;
import com.hrsolution.common.security.Permissions;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * The audit viewer. Read-only, and gated on {@code AUDIT_VIEW}, which by
 * default only SUPER_ADMIN and ADMIN hold.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/audit-logs")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Audit", description = "Security events and entity change history")
public class AuditLogController {

    private final AuditQueryService auditQueryService;

    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.AUDIT_VIEW + "')")
    @Operation(summary = "Search the audit trail",
            description = """
                    Newest first by default. All filters are optional and combine with AND.

                    Useful starting points:
                    * `action=TOKEN_REUSE_DETECTED` - possible refresh-token theft
                    * `action=LOGIN_FAILURE&successful=false` - failed sign-in attempts
                    * `action=ACCOUNT_LOCKED` - accounts locked by repeated failures
                    * `userEmail=someone@example.com` - everything one person did
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "A page of audit entries"),
            @ApiResponse(responseCode = "403", description = "Missing AUDIT_VIEW (ACCESS_DENIED)")
    })
    public PageResponse<AuditLogResponse> search(
            @Parameter(description = "Exact action match")
            @RequestParam(required = false) AuditAction action,
            @Parameter(description = "Exact acting user id")
            @RequestParam(required = false) Long userId,
            @Parameter(description = "Partial, case-insensitive match on the acting user's email")
            @RequestParam(required = false) String userEmail,
            @Parameter(description = "Entity type, e.g. User or Role")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "false to see only failed attempts")
            @RequestParam(required = false) Boolean successful,
            @Parameter(description = "Inclusive lower bound, ISO-8601 UTC, e.g. 2026-10-01T00:00:00Z")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Inclusive upper bound, ISO-8601 UTC")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject
            @PageableDefault(size = 25, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return auditQueryService.search(
                action, userId, userEmail, entityType, successful, from, to, pageable);
    }
}
