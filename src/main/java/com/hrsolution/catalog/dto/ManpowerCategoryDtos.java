package com.hrsolution.catalog.dto;

import com.hrsolution.catalog.entity.SkillLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request and response shapes for the manpower category master. */
public final class ManpowerCategoryDtos {

    private ManpowerCategoryDtos() {
    }

    @Schema(description = "A kind of worker the company supplies")
    public record Response(
            Long id,
            @Schema(example = "SECURITY_GUARD") String code,
            @Schema(example = "Security Guard") String name,
            SkillLevel skillLevel,
            @Schema(description = "Human-readable skill level", example = "Semi-skilled")
            String skillLevelLabel,
            String description,
            int displayOrder,
            boolean active) {
    }

    /** The trimmed shape the public enquiry form's dropdown needs. */
    @Schema(description = "A category, as offered on the public enquiry form")
    public record PublicResponse(
            Long id,
            @Schema(example = "SECURITY_GUARD") String code,
            @Schema(example = "Security Guard") String name,
            @Schema(example = "Semi-skilled") String skillLevelLabel,
            String description) {
    }

    @Schema(description = "Create or replace a manpower category")
    public record Request(

            // Upper snake case enforced because this code is the stable
            // identifier used by Excel imports and exports; a code with spaces
            // or mixed case would make those files fragile.
            @NotBlank(message = "Code is required")
            @Size(max = 40)
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$",
                    message = "must be upper snake case, e.g. SECURITY_GUARD")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "SECURITY_GUARD")
            String code,

            @NotBlank(message = "Name is required")
            @Size(max = 120)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Security Guard")
            String name,

            @NotNull(message = "Skill level is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Selects the applicable minimum wage band")
            SkillLevel skillLevel,

            @Size(max = 500)
            String description,

            @Schema(description = "Lower numbers appear first", defaultValue = "0")
            int displayOrder,

            @Schema(description = "False hides it from new enquiries while keeping history valid",
                    defaultValue = "true")
            Boolean active) {
    }
}
