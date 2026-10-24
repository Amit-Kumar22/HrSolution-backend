package com.hrsolution.recruitment.dto;

import com.hrsolution.catalog.entity.SkillLevel;
import com.hrsolution.recruitment.entity.JobStatus;
import com.hrsolution.common.domain.Shift;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request and response shapes for job adverts.
 *
 * <p>Note the two separate response records. {@link PublicResponse} is not a
 * trimmed-down convenience - it is a different contract. It has no client, no
 * site and no internal status, because a client paying for manpower frequently
 * requires that the vacancy not be attributable to them. Deriving the public
 * shape from the staff one by ignoring fields would make that guarantee a
 * mapping annotation somebody could remove; two records make it structural.
 */
public final class JobDtos {

    private JobDtos() {
    }

    // ==================================================================
    // Staff
    // ==================================================================

    @Schema(name = "Job", description = "A vacancy, as staff see it")
    public record Response(
            Long id,
            String slug,
            String title,
            String summary,
            String description,

            Long categoryId,
            String categoryName,
            SkillLevel skillLevel,

            @Schema(description = "Who the role is for. Never shown on the public site.")
            Long clientId,
            String clientName,
            Long siteId,
            String siteName,
            @Schema(description = "The client demand this advert sources for, if any. "
                    + "Never shown on the public site.")
            Long requisitionId,
            String requisitionNumber,

            String location,
            String city,
            String state,

            int openings,
            @Schema(description = "Applications at SELECTED or beyond, which count against openings")
            long openingsCommitted,
            @Schema(description = "openings minus openingsCommitted; negative means over-committed")
            long openingsRemaining,
            @Schema(description = "Total applications received, in any status")
            long applicationCount,

            BigDecimal salaryMin,
            BigDecimal salaryMax,
            @Schema(description = "\"Rs 18000 - Rs 22000 per month\", or null when undisclosed")
            String salaryRangeLabel,

            Integer experienceMinMonths,
            Integer experienceMaxMonths,
            @Schema(description = "\"2 years - 5 years\", or null when unstated")
            String experienceLabel,

            Shift shift,
            JobStatus status,

            LocalDate expiresOn,
            @Schema(description = "True when the expiry date has passed, whatever the status says")
            boolean expired,
            @Schema(description = "Days until expiry, or null when open-ended or lapsed")
            Long daysUntilExpiry,
            @Schema(description = "True when the advert is live on the public site right now")
            boolean publiclyVisible,

            String metaTitle,
            String metaDescription,
            int displayOrder) {
    }

    @Schema(name = "JobSummary", description = "A vacancy trimmed to what a table row shows")
    public record SummaryResponse(
            Long id,
            String slug,
            String title,
            String categoryName,
            String city,
            int openings,
            long applicationCount,
            JobStatus status,
            LocalDate expiresOn,
            boolean expired,
            boolean publiclyVisible) {
    }

    @Schema(name = "JobRequest", description = "Create or update a job advert")
    public record Request(

            @NotBlank(message = "Slug is required")
            @Size(max = 160)
            @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$",
                    message = "must be lowercase words separated by single hyphens, "
                            + "e.g. security-guard-pune")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The public URL segment. Changing it on a live advert breaks "
                            + "inbound links.",
                    example = "security-guard-pune-plant")
            String slug,

            @NotBlank(message = "Title is required")
            @Size(max = 160)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Security Guard")
            String title,

            @Size(max = 400)
            @Schema(description = "One-line teaser shown in the job list",
                    example = "Day-shift security guards for a manufacturing plant in Pune")
            String summary,

            @Size(max = 20000)
            @Schema(description = "Full advert body. Rich text is fine.")
            String description,

            @NotNull(message = "Category is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Id from GET /manpower-categories")
            Long categoryId,

            @Schema(description = "Which client the role is for. Optional, and never published.")
            Long clientId,

            @Schema(description = "Which site. Must belong to clientId if both are given.")
            Long siteId,

            @Schema(description = "The client demand this advert sources for. Optional - "
                    + "speculative hiring for a role the company expects to need is normal. "
                    + "Must belong to clientId if both are given.")
            Long requisitionId,

            @Size(max = 200)
            @Schema(example = "MIDC Bhosari, Pune")
            String location,

            @Size(max = 100)
            @Schema(example = "Pune")
            String city,

            @Size(max = 100)
            @Schema(example = "Maharashtra")
            String state,

            @Min(value = 1, message = "A job must have at least one opening")
            @Max(value = 10000, message = "must be 10000 or fewer")
            @Schema(defaultValue = "1", example = "12")
            Integer openings,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(description = "Monthly rupees. Leave both salary fields null for "
                    + "\"not disclosed\".", example = "18000.00")
            BigDecimal salaryMin,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(example = "22000.00")
            BigDecimal salaryMax,

            @Min(value = 0, message = "cannot be negative")
            @Max(value = 600, message = "must be 50 years or fewer")
            @Schema(description = "Months. 0 means freshers welcome.", example = "12")
            Integer experienceMinMonths,

            @Min(value = 0, message = "cannot be negative")
            @Max(value = 600, message = "must be 50 years or fewer")
            @Schema(example = "60")
            Integer experienceMaxMonths,

            @Schema(description = "DAY, NIGHT or ROTATIONAL. Night work attracts a shift "
                    + "allowance in most state notifications.")
            Shift shift,

            @Schema(description = "Last day the advert appears. Null means no expiry.",
                    example = "2026-12-31")
            LocalDate expiresOn,

            @Size(max = 160)
            String metaTitle,

            @Size(max = 320)
            String metaDescription,

            @Schema(description = "Lower numbers appear first", defaultValue = "0")
            Integer displayOrder) {
    }

    @Schema(name = "JobStatusRequest", description = "Move a job advert through its lifecycle")
    public record StatusChangeRequest(

            @NotNull(message = "A status is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "DRAFT, OPEN, ON_HOLD, CLOSED or FILLED. Only OPEN is "
                            + "publicly visible.")
            JobStatus status,

            @Size(max = 500)
            @Schema(description = "Why. Recorded in the audit trail.",
                    example = "Client withdrew the requirement")
            String reason) {
    }

    // ==================================================================
    // Public careers site - no client, no site, no internal status
    // ==================================================================

    @Schema(name = "PublicJob", description = "A vacancy as advertised on the public careers page")
    public record PublicResponse(
            @Schema(description = "The URL segment this job was fetched by")
            String slug,
            String title,
            String summary,
            String description,

            @Schema(description = "The kind of work, e.g. \"Security Guard\"")
            String categoryName,
            @Schema(description = "\"Semi-skilled\"")
            String skillLevelLabel,

            String location,
            String city,
            String state,

            int openings,

            @Schema(description = "Null when the salary is not disclosed")
            String salaryRangeLabel,
            @Schema(description = "Null when no experience requirement is stated")
            String experienceLabel,

            Shift shift,

            @Schema(description = "Last day to apply. Null means open until filled.")
            LocalDate expiresOn,

            String metaTitle,
            String metaDescription) {
    }

    @Schema(name = "PublicJobSummary", description = "A vacancy in the public job list")
    public record PublicSummaryResponse(
            String slug,
            String title,
            String summary,
            String categoryName,
            String city,
            String state,
            int openings,
            String salaryRangeLabel,
            String experienceLabel,
            Shift shift,
            LocalDate expiresOn) {
    }
}
