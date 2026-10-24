package com.hrsolution.worker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request and response shapes for a worker's wage structure.
 *
 * <p>Dated history, like rate cards: a revision creates a new row and closes
 * the previous one. There is no update endpoint for a structure that has taken
 * effect, because payslips have already been issued against it.
 */
public final class SalaryStructureDtos {

    private SalaryStructureDtos() {
    }

    @Schema(name = "SalaryStructure", description = "A worker's monthly wage breakdown")
    public record Response(
            Long id,
            Long workerId,
            String workerName,

            BigDecimal basic,
            @Schema(description = "Dearness allowance. Part of the PF base.")
            BigDecimal da,
            BigDecimal hra,
            BigDecimal conveyance,
            BigDecimal otherAllowance,
            @Schema(description = "Sum of all components")
            BigDecimal gross,
            @Schema(description = "basic + DA. The base PF is computed on, which is why HRA is "
                    + "a separate column.")
            BigDecimal providentFundBase,

            BigDecimal otRatePerHour,

            LocalDate effectiveFrom,
            @Schema(description = "Null means this is the current structure")
            LocalDate effectiveTo,
            boolean current,

            String notes) {
    }

    @Schema(name = "SalaryStructureRequest",
            description = "Create a wage structure, superseding the current one")
    public record Request(

            @NotNull(message = "Basic is required")
            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "12000.00")
            BigDecimal basic,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(description = "Dearness allowance", example = "3000.00")
            BigDecimal da,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(example = "2000.00")
            BigDecimal hra,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(example = "800.00")
            BigDecimal conveyance,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "9999999.99", message = "is implausibly large")
            @Schema(example = "200.00")
            BigDecimal otherAllowance,

            @DecimalMin(value = "0.0", message = "cannot be negative")
            @DecimalMax(value = "99999.99", message = "is implausibly large")
            @Schema(example = "95.00")
            BigDecimal otRatePerHour,

            @NotNull(message = "An effective-from date is required")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "The previous structure is closed the day before this date",
                    example = "2026-11-01")
            LocalDate effectiveFrom,

            @Size(max = 500)
            @Schema(example = "Annual revision")
            String notes) {
    }
}
