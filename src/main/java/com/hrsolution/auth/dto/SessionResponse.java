package com.hrsolution.auth.dto;

import com.hrsolution.auth.entity.RefreshToken;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * One active device session, for the "where am I signed in?" screen.
 *
 * <p>Nothing token-derived is exposed - no hash, no family id. The id is the
 * database row id, which is all that is needed to revoke it, and which is
 * useless to anyone who is not already authenticated as this user.
 *
 * @param current whether this is the session making the request, so the UI can
 *                label it and warn that revoking it signs the user out
 */
@Schema(description = "An active login session on one device")
public record SessionResponse(

        Long id,
        @Schema(example = "203.0.113.42")
        String ipAddress,
        @Schema(example = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) ...")
        String userAgent,
        @Schema(description = "When this device signed in")
        Instant createdAt,
        Instant expiresAt,
        @Schema(description = "True for the session that issued the current request")
        boolean current) {

    public static SessionResponse from(RefreshToken token, boolean current) {
        return new SessionResponse(
                token.getId(),
                token.getIpAddress(),
                token.getUserAgent(),
                token.getCreatedAt(),
                token.getExpiresAt(),
                current);
    }
}
