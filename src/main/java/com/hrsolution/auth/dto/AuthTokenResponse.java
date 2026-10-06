package com.hrsolution.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

/**
 * Returned by {@code /auth/login} and {@code /auth/refresh}.
 *
 * <p>Note what is <strong>not</strong> here: the refresh token. It is delivered
 * only as an {@code HttpOnly} cookie, so JavaScript cannot read it and an XSS
 * flaw cannot exfiltrate it. The access token is in the body precisely so the
 * client can hold it in memory rather than in {@code localStorage}, where any
 * injected script could read it and where it would survive the tab closing.
 *
 * @param accessToken the signed RS256 JWT, for the {@code Authorization: Bearer} header
 * @param tokenType   always {@code Bearer}
 * @param expiresIn   seconds until the access token expires
 * @param expiresAt   absolute expiry, to avoid relying on client clock drift
 * @param user        the signed-in user, so the client need not call {@code /auth/me} straight away
 */
@Schema(description = "Access token plus the signed-in user. The refresh token is set as an HttpOnly cookie.")
public record AuthTokenResponse(

        @Schema(example = "eyJraWQiOiJhYmMiLCJhbGciOiJSUzI1NiJ9...")
        String accessToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(description = "Seconds until expiry", example = "900")
        long expiresIn,

        Instant expiresAt,

        CurrentUserResponse user) {

    public static final String BEARER = "Bearer";

    public static AuthTokenResponse of(String accessToken, long expiresIn,
                                       Instant expiresAt, CurrentUserResponse user) {
        return new AuthTokenResponse(accessToken, BEARER, expiresIn, expiresAt, user);
    }
}
