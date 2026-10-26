package com.hrsolution.payroll.dto;

import com.hrsolution.payroll.entity.PayrollComponentCode;
import com.hrsolution.payroll.entity.PayrollComponentType;
import com.hrsolution.payroll.entity.PayrollRunStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PayrollDtos {
    private PayrollDtos() {
    }

    public record GenerateRequest(
            Long clientId,
            @NotNull @Min(1) @Max(12) Integer month,
            @NotNull @Min(2000) @Max(2100) Integer year) {
    }

    public record ComponentResponse(
            Long id,
            PayrollComponentType componentType,
            PayrollComponentCode code,
            BigDecimal amount,
            BigDecimal computedOn,
            BigDecimal ratePercent) {
    }

    public record ItemResponse(
            Long id,
            Long workerId,
            String workerName,
            String employeeCode,
            Long deploymentId,
            Long attendanceSheetId,
            int paidDays,
            int totalDays,
            BigDecimal overtimeHours,
            BigDecimal grossEarnings,
            BigDecimal totalDeductions,
            BigDecimal netPay,
            boolean belowMinimumWage,
            List<ComponentResponse> components) {
    }

    public record Response(
            Long id,
            Long clientId,
            String clientName,
            int month,
            int year,
            PayrollRunStatus status,
            List<PayrollRunStatus> allowedNextStatuses,
            boolean locked,
            BigDecimal totalGross,
            BigDecimal totalDeductions,
            BigDecimal totalNet,
            List<ItemResponse> items,
            Instant processedAt,
            String processedBy,
            Instant approvedAt,
            String approvedBy,
            Instant paidAt,
            Instant lockedAt,
            String notes,
            Instant createdAt) {
    }

    public record SummaryResponse(
            Long id,
            Long clientId,
            String clientName,
            int month,
            int year,
            PayrollRunStatus status,
            BigDecimal totalGross,
            BigDecimal totalNet,
            int itemCount,
            Instant processedAt,
            Instant approvedAt,
            Instant paidAt,
            Instant lockedAt) {
    }
}
