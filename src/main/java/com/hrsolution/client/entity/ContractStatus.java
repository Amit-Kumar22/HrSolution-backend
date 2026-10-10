package com.hrsolution.client.entity;

/** Lifecycle of a client contract. */
public enum ContractStatus {

    /** Being negotiated. Carries no commercial force. */
    DRAFT,

    /** Signed and in force. Phase 9 bills the service charge from this one. */
    ACTIVE,

    /** Passed its end date. Set by the scheduled job in Phase 10. */
    EXPIRED,

    /** Ended early by either party. */
    TERMINATED;

    /** Whether this contract's terms should be applied to new billing. */
    public boolean isInForce() {
        return this == ACTIVE;
    }
}
