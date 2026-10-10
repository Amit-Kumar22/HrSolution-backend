package com.hrsolution.catalog.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Request and response shapes for the skills master. */
public final class SkillDtos {

    private SkillDtos() {
    }

    @Schema(name = "Skill", description = "A capability or certification a worker can hold")
    public record Response(
            Long id,
            @Schema(example = "PSARA Trained") String name,
            String description,
            boolean active) {
    }

    @Schema(name = "SkillRequest", description = "Create or update a skill")
    public record Request(

            @NotBlank(message = "Name is required")
            @Size(max = 120)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "PSARA Trained")
            String name,

            @Size(max = 500)
            String description,

            @Schema(defaultValue = "true")
            Boolean active) {
    }

    @Schema(name = "CategorySkillsRequest", description = "Replace the skills a category typically requires")
    public record AssignToCategoryRequest(

            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Full replacement set of skill ids. Advisory only - a "
                            + "deployment is never blocked for a missing skill.")
            Set<Long> skillIds) {
    }
}
