package com.hrsolution.attendance.dto;

import com.hrsolution.attendance.entity.AttendanceEntryStatus;
import com.hrsolution.attendance.entity.AttendanceSheetStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class AttendanceDtos {
    private AttendanceDtos() {
    }

    public record EntryRequest(
            @NotNull Long workerId,
            @NotNull Long deploymentId,
            @NotNull LocalDate attendanceDate,
            @NotNull AttendanceEntryStatus status,
            BigDecimal overtimeHours,
            String remarks) {
    }

    public record BulkEntryRequest(
            @NotNull List<EntryRequest> entries) {
    }

    public record EntryResponse(
            Long id,
            Long workerId,
            String workerName,
            String employeeCode,
            Long deploymentId,
            LocalDate attendanceDate,
            AttendanceEntryStatus status,
            BigDecimal overtimeHours,
            String remarks) {
    }

    public record Response(
            Long id,
            Long clientId,
            String clientName,
            Long siteId,
            String siteCode,
            String siteName,
            int month,
            int year,
            AttendanceSheetStatus status,
            List<AttendanceEntryStatus> allowedNextStatuses,
            boolean locked,
            Instant submittedAt,
            String submittedBy,
            Instant approvedAt,
            String approvedBy,
            Instant lockedAt,
            String lockedBy,
            String unlockReason,
            Instant unlockedAt,
            String unlockedBy,
            List<EntryResponse> entries,
            Instant createdAt) {
    }

    public record SummaryResponse(
            Long id,
            Long clientId,
            String clientName,
            Long siteId,
            String siteCode,
            String siteName,
            int month,
            int year,
            AttendanceSheetStatus status,
            int entryCount,
            Instant submittedAt,
            Instant approvedAt,
            Instant lockedAt) {
    }

    public record UnlockRequest(
            @NotNull String reason) {
    }
}
