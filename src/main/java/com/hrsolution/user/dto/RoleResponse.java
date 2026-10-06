package com.hrsolution.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Set;

/**
 * A role and the permissions it grants.
 *
 * @param systemRole seeded roles that application logic refers to by name;
 *                   they cannot be deleted or renamed, though their permission
 *                   set remains editable
 */
@Schema(description = "A role with its granted permissions")
public record RoleResponse(
        Long id,
        @Schema(example = "ACCOUNTS")
        String name,
        String description,
        boolean systemRole,
        Set<String> permissions) {
}
