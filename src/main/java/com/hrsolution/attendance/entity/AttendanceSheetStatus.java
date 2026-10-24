package com.hrsolution.attendance.entity;

import java.util.List;

public enum AttendanceSheetStatus {

    DRAFT,
    SUBMITTED,
    CLIENT_APPROVED,
    LOCKED;

    public boolean canEdit() {
        return this == DRAFT;
    }

    public boolean canSubmit() {
        return this == DRAFT;
    }

    public boolean canClientApprove() {
        return this == SUBMITTED;
    }

    public boolean canLock() {
        return this == CLIENT_APPROVED;
    }

    public boolean isLocked() {
        return this == LOCKED;
    }

    public List<AttendanceSheetStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> List.of(SUBMITTED);
            case SUBMITTED -> List.of(CLIENT_APPROVED);
            case CLIENT_APPROVED -> List.of(LOCKED);
            case LOCKED -> List.of();
        };
    }
}
