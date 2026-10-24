package com.hrsolution.requisition.dto;

import com.hrsolution.catalog.entity.SkillLevel;
import com.hrsolution.common.domain.Shift;
import com.hrsolution.requisition.entity.RequisitionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Request and response shapes for client requisitions. */
public final class RequisitionDtos {

    private RequisitionDtos() {
    }

    @Schema(name = "Requisition", description = "A client's request for workers")
    public record Response(
            Long id,
            @Schema(description = "Derived from the id; stable and quotable", example = "REQ-000042")
            String requisitionNumber,

            Long clientId,
            String clientName,
            Long siteId,
            String siteCode,
            String siteName,
            @Schema(description = "The site's state code. Drives minimum wage and professional "
                    + "tax for anyone deployed here.")
            String siteStateCode,

            Long categoryId,
            String categoryName,
            @Schema(description = "The grade asked for: the explicit override, else the "
                    + "category's own")
            SkillLevel effectiveSkillLevel,
            SkillLevel skillLevel,

            int quantityRequired,
            @Schema(description = "Active deployments against this requisition. Counted from "
                    + "the deployments table, never stored.")
            long quantityDeployed,
            @Schema(description = "quantityRequired minus quantityDeployed, floored at zero")
            long quantityOutstanding,

            Shift shift,
            LocalDate requiredFrom,
            @Schema(description = "Null means ongoing")
            LocalDate requiredTo,

            RequisitionStatus status,
            @Schema(description = "The statuses this requisition may move to next. Drive the "
                    + "status control from this, not the full enum.")
            List<RequisitionStatus> allowedNextStatuses,
            boolean terminal,
            @Schema(description = "True when workers may be deployed against it now")
            boolean acceptsDeployments,
            @Schema(description = "True when it starts within the next 7 days and is not yet "
                    + "filled - the list operations works from")
            boolean urgent,

            Long raisedByUserId,
            String raisedByName,
            Long approvedByUserId,
            String approvedByName,
            Instant approvedAt,

            String rejectionReason,
            String notes,
            Instant createdAt) {
    }

    @Schema(name = "RequisitionSummary", description = "A requisition trimmed to a table row")
    public record SummaryResponse(
            Long id,
            String requisitionNumber,
            String clientName,
            String siteCode,
            String categoryName,
            int quantityRequired,
            long quantityDeployed,
            long quantityOutstanding,
            LocalDate requiredFrom,
            RequisitionStatus status,
            boolean urgent) {
    }

    @Schema(name = "RequisitionDetail",
            description = "A requisition with its approval trail and the workers deployed against it")
    public record DetailResponse(
            Response requisition,
            @Schema(description = "Oldest first. The first entry has a null fromStatus and "
                    + "records creation.")
            List<StatusHistoryEntry> history) {
    }

    @Schema(name = "RequisitionStatusHistoryEntry",
            description = "One recorded move of a requisition")
    public record StatusHistoryEntry(
            Long id,
            @Schema(description = "Null on the first entry, which records creation")
            RequisitionStatus fromStatus,
            RequisitionStatus toStatus,
            Long changedByUserId,
            String changedByEmail,
            String remarks,
            Instant createdAt) {
    }

    /**
     * The shape a client portal user sees.
     *
     * <p>No internal notes, no approver identity, and the status comes through
     * as a plain-language label. A client is entitled to know where their
     * request stands, not who inside the company handled it.
     */
    @Schema(name = "ClientRequisition",
            description = "A requisition as the client who raised it sees it")
    public record ClientResponse(
            Long id,
            String requisitionNumber,
            Long siteId,
            String siteCode,
            String siteName,
            String categoryName,
            SkillLevel skillLevel,
            int quantityRequired,
            long quantityDeployed,
            long quantityOutstanding,
            Shift shift,
            LocalDate requiredFrom,
            LocalDate requiredTo,
            RequisitionStatus status,
            @Schema(description = "Plain-language status for the client",
                    example = "Partly deployed - the rest is on the way")
            String statusLabel,
            boolean terminal,
            @Schema(description = "True while the client can still edit or submit it")
            boolean editable,
            Instant createdAt) {
    }

    @Schema(name = "RequisitionRequest", description = "Raise or amend a requisition")
    public record Request(

            @NotNull(message = "A site is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Which site the workers are needed at. Mandatory: the site's "
                            + "state decides the minimum wage and the professional tax slab, so "
                            + "a requisition without one cannot be costed.")
            Long siteId,

            @NotNull(message = "A category is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Id from GET /manpower-categories")
            Long categoryId,

            @NotNull(message = "A quantity is required")
            @Min(value = 1, message = "must be at least 1")
            @Max(value = 10000, message = "must be 10000 or fewer")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
            Integer quantityRequired,

            @Schema(description = "Overrides the category's default grade. Leave null to use it.")
            SkillLevel skillLevel,

            @Schema(description = "DAY, NIGHT or ROTATIONAL")
            Shift shift,

            @NotNull(message = "A start date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-11-01")
            LocalDate requiredFrom,

            @Schema(description = "Leave null for an ongoing requirement", example = "2027-03-31")
            LocalDate requiredTo,

            @Size(max = 2000)
            String notes) {
    }

    @Schema(name = "RequisitionStatusRequest",
            description = "Move a requisition through its lifecycle")
    public record StatusChangeRequest(

            @NotNull(message = "A status is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Must be one of the current status's allowedNextStatuses")
            RequisitionStatus status,

            @Size(max = 1000)
            @Schema(description = "Recorded in the approval trail. Required when rejecting.",
                    example = "No trained guards available for a 1 November start")
            String remarks) {
    }
}
