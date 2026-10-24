package com.hrsolution.leave.entity;

import java.util.List;

public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    public boolean canCancel() {
        return this == PENDING;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    public List<LeaveStatus> allowedNext() {
        return switch (this) {
            case PENDING -> List.of(APPROVED, REJECTED, CANCELLED);
            case APPROVED, REJECTED, CANCELLED -> List.of();
        };
    }
}
