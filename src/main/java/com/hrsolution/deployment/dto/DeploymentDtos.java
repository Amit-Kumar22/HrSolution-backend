package com.hrsolution.deployment.dto;

import com.hrsolution.common.domain.Shift;
import com.hrsolution.deployment.entity.DeploymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Request and response shapes for deployments. */
public final class DeploymentDtos {

    private DeploymentDtos() {
    }

    @Schema(name = "Deployment", description = "A worker posted to a client site")
    public record Response(
            Long id,

            Long workerId,
            String employeeCode,
            String workerName,

            Long clientId,
            String clientName,
            Long siteId,
            String siteCode,
            String siteName,

            Long requisitionId,
            String requisitionNumber,

            Long categoryId,
            @Schema(description = "The role they are posted as, which may differ from their usual")
            String categoryName,

            LocalDate startDate,
            @Schema(description = "Null means currently deployed")
            LocalDate endDate,
            Shift shift,

            @Schema(description = "Snapshotted from the rate card applicable on startDate. A "
                    + "later rate change does not restate it.")
            BigDecimal monthlyWage,
            @Schema(description = "Snapshotted, as above")
            BigDecimal billingRate,
            @Schema(description = "billingRate minus monthlyWage, before employer PF/ESI and "
                    + "the contract service charge")
            BigDecimal grossMarginPerMonth,
            @Schema(description = "True when the client is billed less than the worker is paid")
            boolean billingBelowWage,

            DeploymentStatus status,
            @Schema(description = "True when active and today falls inside the date range")
            boolean inForceToday,
            @Schema(description = "Days served up to today, inclusive of the start")
            long daysServed,

            LocalDate releasedOn,
            String releaseReason,

            @Schema(description = "\"Bharat Textiles / PUNE-PLANT-1 — Security Guard\"")
            String displayLabel,

            String notes,
            Instant createdAt) {
    }

    @Schema(name = "DeploymentSummary", description = "A deployment trimmed to a table row")
    public record SummaryResponse(
            Long id,
            Long workerId,
            String employeeCode,
            String workerName,
            String clientName,
            String siteCode,
            String categoryName,
            LocalDate startDate,
            LocalDate endDate,
            DeploymentStatus status,
            boolean inForceToday) {
    }

    @Schema(name = "DeploymentRequest", description = "Post a worker to a client site")
    public record Request(

            @NotNull(message = "A worker is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Long workerId,

            @NotNull(message = "A site is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The client is taken from the site, so there is no clientId "
                            + "to send - and no way to post a worker to a site belonging to a "
                            + "different client.")
            Long siteId,

            @Schema(description = "The demand this fulfils. Optional, but supplying it is what "
                    + "advances the requisition's fulfilment.")
            Long requisitionId,

            @Schema(description = "The role to post them as. Defaults to the requisition's "
                    + "category when one is given.")
            Long categoryId,

            @NotNull(message = "A start date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01")
            LocalDate startDate,

            @Schema(description = "Leave null for an open-ended posting. Note an open-ended "
                    + "posting blocks the worker from being posted anywhere else.",
                    example = "2027-03-31")
            LocalDate endDate,

            Shift shift,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(description = "Overrides the rate card. Leave null to use the resolved "
                    + "rate, which is what you normally want.")
            BigDecimal monthlyWage,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(description = "Overrides the rate card, as above")
            BigDecimal billingRate,

            @Size(max = 2000)
            String notes) {
    }

    @Schema(name = "DeploymentEndRequest", description = "End a deployment")
    public record EndRequest(

            @NotNull(message = "A status is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "RELEASED when pulled out early, COMPLETED when it ran its "
                            + "course, TRANSFERRED when the worker is moving elsewhere")
            DeploymentStatus status,

            @NotNull(message = "An end date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Also sets the end date, so the worker stops being blocked "
                            + "from other postings",
                    example = "2026-12-31")
            LocalDate endedOn,

            @NotNull(message = "A reason is required")
            @Size(max = 500)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Client reduced the guard strength from 12 to 8")
            String reason) {
    }

    @Schema(name = "DeploymentTransferRequest",
            description = "Move a worker from one site to another in one step")
    public record TransferRequest(

            @NotNull(message = "A destination site is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            Long toSiteId,

            @NotNull(message = "A transfer date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The current posting ends the day before this date, and the "
                            + "new one starts on it - so there is no gap and no overlap.",
                    example = "2026-12-01")
            LocalDate effectiveFrom,

            @Schema(description = "The demand the new posting fulfils")
            Long requisitionId,

            @Schema(description = "The role at the new site. Defaults to the current one.")
            Long categoryId,

            Shift shift,

            @NotNull(message = "A reason is required")
            @Size(max = 500)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    example = "Moved to the Nashik plant at the client's request")
            String reason) {
    }

    /**
     * What a client sees of the people posted to their sites.
     *
     * <p>No wage, no billing rate, no margin. A client is entitled to know who
     * is on their premises and in what role - the commercial terms are on their
     * contract and rate card, not on each individual posting, and showing a
     * per-person wage here would expose how the headline rate is composed.
     */
    @Schema(name = "ClientDeployment",
            description = "A worker posted to the client's site, as the client sees it")
    public record ClientResponse(
            Long id,
            String employeeCode,
            String workerName,
            String siteCode,
            String siteName,
            String categoryName,
            Shift shift,
            LocalDate startDate,
            LocalDate endDate,
            DeploymentStatus status,
            boolean inForceToday,
            @Schema(description = "True when the worker's police verification is on file and "
                    + "clear - what the principal employer is liable for checking")
            boolean policeVerified) {
    }
}
