package com.hrsolution.leave.dto;

import com.hrsolution.leave.entity.LeaveStatus;
import com.hrsolution.leave.entity.LeaveType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class LeaveDtos {
    private LeaveDtos() {
    }

    public record Request(
            @NotNull Long workerId,
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotNull LeaveType leaveType,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record SelfRequest(
            @NotNull LocalDate fromDate,
            @NotNull LocalDate toDate,
            @NotNull LeaveType leaveType,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record ActionRequest(
            @Size(max = 500) String remarks,
            @Size(max = 500) String rejectionReason) {
    }

    public record Response(
            Long id,
            Long workerId,
            String workerName,
            String employeeCode,
            LocalDate fromDate,
            LocalDate toDate,
            LeaveType leaveType,
            LeaveStatus status,
            List<LeaveStatus> allowedNextStatuses,
            boolean terminal,
            String reason,
            String remarks,
            String approvedByName,
            Instant approvedAt,
            String rejectionReason,
            Instant createdAt) {
    }
}
