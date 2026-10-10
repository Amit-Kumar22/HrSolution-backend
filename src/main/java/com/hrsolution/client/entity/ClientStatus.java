package com.hrsolution.client.entity;

/**
 * Lifecycle of a client company.
 *
 * <p>Distinct from the {@code UserStatus} of the people who log in for that
 * client: suspending a client must stop new work without disabling individual
 * accounts, and a person leaving must not suspend the company.
 */
public enum ClientStatus {

    /** Self-registered from the website; awaiting an administrator's approval. */
    PENDING_APPROVAL,

    /** Approved and trading. The only status that permits new requisitions. */
    ACTIVE,

    /** Temporarily stopped - usually non-payment. Existing deployments stand. */
    SUSPENDED,

    /** No longer a client. Kept for the historical record. */
    INACTIVE,

    /** Registration declined. Terminal. */
    REJECTED;

    /**
     * Whether new commercial activity is allowed - raising a requisition,
     * deploying a worker, signing a contract.
     *
     * <p>Note that reading and invoicing stay possible for a suspended client:
     * work already done still has to be billed and collected.
     */
    public boolean allowsNewWork() {
        return this == ACTIVE;
    }
}
