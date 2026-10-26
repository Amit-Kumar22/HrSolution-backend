package com.hrsolution.payroll.entity;

import java.util.List;

public enum PayrollRunStatus {
    DRAFT,
    PROCESSED,
    APPROVED,
    PAID,
    LOCKED;

    public boolean canProcess() {
        return this == DRAFT;
    }

    public boolean canApprove() {
        return this == PROCESSED;
    }

    public boolean canMarkPaid() {
        return this == APPROVED;
    }

    public boolean canLock() {
        return this == PAID;
    }

    public boolean isLocked() {
        return this == LOCKED;
    }

    public List<PayrollRunStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> List.of(PROCESSED);
            case PROCESSED -> List.of(APPROVED);
            case APPROVED -> List.of(PAID);
            case PAID -> List.of(LOCKED);
            case LOCKED -> List.of();
        };
    }
}
