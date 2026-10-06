package com.hrsolution.auth.dto;

import com.hrsolution.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Result of a successful registration.
 *
 * <p>No tokens are issued: a candidate must verify their email first, and a
 * client must additionally be approved. {@link #status} tells the client which
 * of those it is waiting on, and {@link #message} is text it can show as-is.
 */
@Schema(description = "Registration accepted; the account is not yet usable")
public record RegistrationResponse(

        Long userId,
        String email,
        UserStatus status,
        @Schema(example = "Registered. Check your inbox for the verification link.")
        String message) {
}
