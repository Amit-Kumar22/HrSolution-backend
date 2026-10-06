package com.hrsolution.auth.dto;

import com.hrsolution.user.entity.User;
import com.hrsolution.user.entity.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Set;

/**
 * The signed-in user, returned by {@code GET /auth/me} and embedded in the
 * login response.
 *
 * <p>{@link #permissions} is included so a client can decide what to show
 * without guessing from role names. It is a <em>display</em> aid only - the
 * server re-derives authorities from the database on every request, so editing
 * this list client-side grants nothing.
 */
@Schema(description = "The currently authenticated user with roles and permissions")
public record CurrentUserResponse(

        Long id,
        @Schema(example = "admin@hrsolution.local")
        String email,
        String firstName,
        String lastName,
        @Schema(example = "Asha Patil")
        String fullName,
        String phone,
        UserStatus status,
        boolean emailVerified,

        @Schema(example = "[\"ADMIN\"]")
        Set<String> roles,

        @Schema(description = "Union of permissions across the user's roles; for UI decisions only",
                example = "[\"CLIENT_READ\",\"CLIENT_WRITE\"]")
        Set<String> permissions,

        Instant lastLoginAt) {

    /**
     * Requires {@code roles} and their {@code permissions} to be initialised -
     * load the user through {@code UserRepository.findActiveBy...WithRoles}.
     */
    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.fullName(),
                user.getPhone(),
                user.getStatus(),
                user.isEmailVerified(),
                user.roleNames(),
                user.permissionNames(),
                user.getLastLoginAt());
    }
}
