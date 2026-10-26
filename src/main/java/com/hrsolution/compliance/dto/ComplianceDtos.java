package com.hrsolution.compliance.dto;

import com.hrsolution.compliance.entity.ChallanType;
import com.hrsolution.compliance.entity.LicenceStatus;
import com.hrsolution.compliance.entity.LicenceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class ComplianceDtos {
    private ComplianceDtos() {
    }

    public record LicenceRequest(
            @NotNull LicenceType licenceType,
            @NotBlank String licenceNumber,
            Long clientId,
            LocalDate issueDate,
            LocalDate expiryDate,
            String documentPath,
            LicenceStatus status,
            String issuingAuthority,
            String notes) {
    }

    public record LicenceResponse(
            Long id,
            LicenceType licenceType,
            String licenceNumber,
            Long clientId,
            String clientName,
            LocalDate issueDate,
            LocalDate expiryDate,
            String documentPath,
            LicenceStatus status,
            String issuingAuthority,
            String notes,
            long daysUntilExpiry,
            boolean expiringSoon,
            Instant createdAt) {
    }

    public record ChallanRequest(
            @NotNull ChallanType challanType,
            @NotNull Integer month,
            @NotNull Integer year,
            @NotNull BigDecimal amount,
            LocalDate paidOn,
            String challanNumber,
            String documentPath,
            Boolean returnFiled,
            String notes) {
    }

    public record ChallanResponse(
            Long id,
            ChallanType challanType,
            Integer month,
            Integer year,
            BigDecimal amount,
            LocalDate paidOn,
            String challanNumber,
            String documentPath,
            boolean returnFiled,
            String notes,
            Instant createdAt) {
    }

    public record ReminderResponse(
            Long licenceId,
            String licenceType,
            String licenceNumber,
            String clientName,
            LocalDate expiryDate,
            long daysUntilExpiry,
            String reminderLevel) {
    }
}
