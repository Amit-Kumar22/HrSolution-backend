package com.hrsolution.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A plain acknowledgement, for endpoints that have nothing to return.
 *
 * <p>Used deliberately by {@code /auth/forgot-password} and
 * {@code /auth/resend-verification}, which return the same message whether or
 * not the email exists. Anything that varied with the outcome would turn the
 * endpoint into an account-enumeration oracle.
 */
@Schema(description = "A simple acknowledgement")
public record MessageResponse(
        @Schema(example = "If that email is registered, a reset link has been sent.")
        String message) {

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
