package com.hrsolution.client.dto;

import com.hrsolution.client.entity.ContractStatus;
import com.hrsolution.client.entity.ServiceChargeType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Request and response shapes for client contracts. */
public final class ClientContractDtos {

    private ClientContractDtos() {
    }

    @Schema(name = "ClientContract", description = "A commercial agreement with a client")
    public record Response(
            Long id,
            Long clientId,
            String clientName,
            String contractNumber,
            String title,

            LocalDate startDate,
            @Schema(description = "Null means open-ended; expiry reminders need a set date")
            LocalDate endDate,

            ServiceChargeType serviceChargeType,
            @Schema(description = "A percentage when the type is PERCENTAGE, otherwise rupees "
                    + "per deployed worker per month")
            BigDecimal serviceChargeValue,
            @Schema(description = "Human-readable, e.g. \"8.50% of wages\" or \"Rs 1200 per worker\"")
            String serviceChargeLabel,

            Integer paymentTermsDays,
            @Schema(description = "The terms that actually apply: the contract's, else the client's")
            int effectivePaymentTermsDays,

            ContractStatus status,
            @Schema(description = "True when ACTIVE and today falls inside the date range")
            boolean inForceToday,
            @Schema(description = "Days until the end date, or null when open-ended or past")
            Long daysUntilExpiry,

            LocalDate terminatedOn,
            String terminationReason,
            String notes,

            @Schema(description = "Number of documents uploaded against this contract")
            int documentCount) {
    }

    @Schema(name = "ClientContractRequest", description = "Create or update a contract")
    public record Request(

            @NotBlank(message = "Contract number is required")
            @Size(max = 60)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "BT/HRS/2026-27/01")
            String contractNumber,

            @Size(max = 200)
            @Schema(example = "Manpower supply - Pune and Nagpur plants")
            String title,

            @NotNull(message = "Start date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "2026-04-01")
            LocalDate startDate,

            @Schema(description = "Leave null for an open-ended contract. Note that an "
                    + "open-ended contract will conflict with any later one.",
                    example = "2027-03-31")
            LocalDate endDate,

            @NotNull(message = "Service charge type is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            ServiceChargeType serviceChargeType,

            // The upper bound differs by type and cannot be expressed with a
            // single annotation, so ClientContractService checks the
            // percentage ceiling. This only catches the obvious nonsense.
            @NotNull(message = "Service charge value is required")
            @DecimalMin(value = "0.00", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "8.50")
            BigDecimal serviceChargeValue,

            @Min(value = 0, message = "must be 0 or more days")
            @Max(value = 365, message = "must be 365 days or fewer")
            @Schema(description = "Overrides the client's default credit period when set")
            Integer paymentTermsDays,

            @Size(max = 2000)
            String notes) {
    }

    @Schema(name = "ContractTerminateRequest", description = "Terminate a contract early")
    public record TerminateRequest(

            @NotNull(message = "Termination date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            LocalDate terminatedOn,

            @NotBlank(message = "A reason is required")
            @Size(max = 500)
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "Recorded on the contract and in the audit log")
            String reason) {
    }
}
