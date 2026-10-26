package com.hrsolution.billing.entity;

import java.util.List;

public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PARTIALLY_PAID,
    PAID,
    CANCELLED;

    public boolean canEdit() {
        return this == DRAFT;
    }

    public boolean canIssue() {
        return this == DRAFT;
    }

    public boolean isEditable() {
        return this == DRAFT;
    }

    public boolean isIssued() {
        return this == ISSUED || this == PARTIALLY_PAID || this == PAID;
    }

    public List<InvoiceStatus> allowedNext() {
        return switch (this) {
            case DRAFT -> List.of(ISSUED, CANCELLED);
            case ISSUED -> List.of(PARTIALLY_PAID, PAID, CANCELLED);
            case PARTIALLY_PAID -> List.of(PAID, CANCELLED);
            case PAID, CANCELLED -> List.of();
        };
    }
}
