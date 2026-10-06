package com.hrsolution.user.dto;

import com.hrsolution.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

/**
 * A user account as shown on the admin screens.
 *
 * <p>No password hash, and no token version - neither is any administrator's
 * business, and leaking a hash would hand an attacker something to crack
 * offline.
 */
@Schema(description = "A user account, for administration")
public record UserResponse(

        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String phone,
        UserStatus status,
        boolean emailVerified,

        @Schema(description = "True while a failed-login lockout is still in force")
        boolean locked,
        @Schema(description = "Seconds until the lockout expires; 0 when not locked")
        long lockRemainingSeconds,
        int failedAttempts,

        Set<String> roles,

        @Schema(description = "Self-declared company name, for a pending client registration")
        String pendingCompanyName,

        Instant lastLoginAt,
        String lastLoginIp,
        Instant createdAt) {
}
