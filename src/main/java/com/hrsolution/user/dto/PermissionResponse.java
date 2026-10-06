package com.hrsolution.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A single permission from the catalogue")
public record PermissionResponse(
        Long id,
        @Schema(example = "PAYROLL_PROCESS")
        String name,
        @Schema(description = "Grouping for the admin screen", example = "PAYROLL")
        String module,
        String description) {
}
