package com.hrsolution.auth.controller;

import com.hrsolution.audit.entity.AuditAction;
import com.hrsolution.audit.service.AuditService;
import com.hrsolution.auth.dto.MessageResponse;
import com.hrsolution.auth.dto.SessionResponse;
import com.hrsolution.auth.entity.RevokedReason;
import com.hrsolution.auth.service.RefreshCookieService;
import com.hrsolution.auth.service.RefreshTokenService;
import com.hrsolution.common.security.AuthenticatedUser;
import com.hrsolution.common.web.ApiPaths;
import com.hrsolution.common.web.RequestContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * "Where am I signed in?" - the active device sessions of the calling user.
 *
 * <p>One row per live refresh token, so a user can spot a session they do not
 * recognise and end it. No permission is required beyond being signed in: these
 * are only ever the caller's own sessions, enforced by an ownership check in
 * {@link RefreshTokenService#revokeSession}.
 */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth/sessions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Authentication", description = "Registration, sign-in, token refresh and credential management")
public class SessionController {

    private final RefreshTokenService refreshTokenService;
    private final RefreshCookieService refreshCookieService;
    private final AuditService auditService;

    @GetMapping
    @Operation(summary = "List your active sessions",
            description = "One entry per signed-in device, newest first. The entry flagged "
                    + "'current' is the session making this request.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Active sessions"),
            @ApiResponse(responseCode = "401", description = "Not signed in (UNAUTHENTICATED)")
    })
    public List<SessionResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
                                      HttpServletRequest httpRequest) {
        // Identify the calling device by resolving its cookie to a row id, so
        // the UI can label it and warn that revoking it signs the user out.
        Long currentSessionId = refreshCookieService.read(httpRequest)
                .map(refreshTokenService::sessionIdOf)
                .orElse(null);

        return refreshTokenService.activeSessions(caller.id()).stream()
                .map(token -> SessionResponse.from(token,
                        Objects.equals(token.getId(), currentSessionId)))
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    @Operation(summary = "Revoke one session",
            description = "Signs out the chosen device. A session id belonging to another user is "
                    + "reported as 'not found' rather than 'forbidden', so ids cannot be probed.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session revoked"),
            @ApiResponse(responseCode = "401",
                    description = "Not signed in, or the session does not belong to you (UNAUTHENTICATED)")
    })
    public MessageResponse revoke(@AuthenticationPrincipal AuthenticatedUser caller,
                                  @PathVariable Long sessionId,
                                  HttpServletRequest httpRequest) {
        refreshTokenService.revokeSession(caller.id(), sessionId, RevokedReason.SESSION_REVOKED);

        auditService.recordSecurityEvent(AuditAction.SESSION_REVOKED, caller.id(), caller.email(),
                true, "Revoked session " + sessionId, RequestContext.from(httpRequest));

        return MessageResponse.of("Session revoked.");
    }
}
