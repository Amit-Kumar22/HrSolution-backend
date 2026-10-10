package com.hrsolution.client.dto;

import com.hrsolution.catalog.entity.SkillLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request and response shapes for rate cards. */
public final class RateCardDtos {

    private RateCardDtos() {
    }

    @Schema(name = "RateCard", description = """
            The agreed wage and billing rate for one category at one client.

            Rate cards are dated history: a change creates a new row and closes the old one, \
            so that recomputing an earlier month's payroll uses the rate that applied then.""")
    public record Response(
            Long id,
            Long clientId,
            String clientName,

            Long categoryId,
            String categoryName,
            SkillLevel skillLevel,

            @Schema(description = "Null means the rate covers every site of this client")
            Long siteId,
            String siteName,
            @Schema(description = "True when tied to one site, which takes precedence over a "
                    + "client-wide rate for the same category")
            boolean siteSpecific,

            @Schema(description = "Monthly wage payable to the worker")
            BigDecimal monthlyWage,
            @Schema(description = "Monthly amount billed per worker, before employer PF/ESI, "
                    + "service charge and GST")
            BigDecimal billingRate,
            @Schema(description = "billingRate minus monthlyWage. Negative means the rate card "
                    + "is wrong - we would bill less than we pay.")
            BigDecimal grossMarginPerWorker,
            @Schema(description = "True when billingRate is below monthlyWage")
            boolean billingBelowWage,

            BigDecimal otRatePerHour,
            int shiftHours,

            LocalDate effectiveFrom,
            @Schema(description = "Null means this is the current rate")
            LocalDate effectiveTo,
            boolean current,

            String notes) {
    }

    @Schema(name = "RateCardRequest", description = "Create a rate card, or supersede the current one")
    public record Request(

            @NotNull(message = "Category is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Long categoryId,

            @Schema(description = "Leave null for a rate covering every site of this client. "
                    + "Set it to override the client-wide rate at one site - useful when "
                    + "plants sit in different states with different minimum wages.")
            Long siteId,

            @NotNull(message = "Monthly wage is required")
            @DecimalMin(value = "0.00", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "18000.00")
            BigDecimal monthlyWage,

            @NotNull(message = "Billing rate is required")
            @DecimalMin(value = "0.00", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "22500.00")
            BigDecimal billingRate,

            @DecimalMin(value = "0.00", message = "cannot be negative")
            @DecimalMax(value = "99999.99", message = "is implausibly large")
            BigDecimal otRatePerHour,

            @Min(value = 1, message = "must be at least 1 hour")
            @Max(value = 24, message = "cannot exceed 24 hours")
            @Schema(defaultValue = "8")
            Integer shiftHours,

            @NotNull(message = "Effective-from date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The date this rate starts applying. Any open rate for the "
                            + "same category and site is closed off the day before.",
                    example = "2026-04-01")
            LocalDate effectiveFrom,

            @Size(max = 500)
            String notes) {
    }

    @Schema(name = "ResolvedRateCard", description = "The rate resolved as applicable for a date, with how it was chosen")
    public record ResolvedResponse(
            @Schema(description = "The applicable rate card, or null when none covers the date")
            Response rateCard,
            @Schema(description = "How the rate was selected, or why none was found",
                    example = "Site-specific rate effective from 2026-04-01")
            String resolution,
            @Schema(description = "True when more than one row matched, which means the rate "
                    + "cards for this combination overlap and need correcting")
            boolean ambiguous) {
    }
}
