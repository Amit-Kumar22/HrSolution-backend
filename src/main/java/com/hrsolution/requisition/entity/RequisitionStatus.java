package com.hrsolution.requisition.entity;

import java.util.Set;

/**
 * Lifecycle of a client's request for workers.
 *
 * <p>A state machine for the same reason {@code ApplicationStatus} is one: this
 * is driven from three places - the client portal, the operations console, and
 * the deployment service that advances fulfilment as workers are posted. A rule
 * enforced in one of them is a rule the other two can bypass.
 *
 * <p>The two refusals that matter:
 *
 * <ul>
 *   <li><strong>{@code DRAFT} cannot be approved.</strong> Approving commits the
 *       company to supplying people, so the client has to submit it first.
 *       Otherwise operations could approve a requirement the client was still
 *       editing.</li>
 *   <li><strong>Nothing leaves a terminal state.</strong> Reopening a closed or
 *       rejected requisition in place would erase the record of the decision.
 *       A revived requirement is a new requisition, which stays visible.</li>
 * </ul>
 */
public enum RequisitionStatus {

    /** Being written by the client. Carries no commitment either way. */
    DRAFT,

    /** Submitted for approval. Operations now owns the decision. */
    SUBMITTED,

    /** Accepted. The company has committed to supplying the workers. */
    APPROVED,

    /** Recruitment and deployment have started; nobody is posted yet. */
    IN_PROGRESS,

    /** Some of the requested headcount is deployed, but not all of it. */
    PARTIALLY_FULFILLED,

    /** The full requested headcount is deployed. */
    FULFILLED,

    /** Finished with, whether or not it was ever filled. Terminal. */
    CLOSED,

    /** Refused by operations - no capacity, or commercially unviable. Terminal. */
    REJECTED,

    /** Withdrawn by the client before it was filled. Terminal. */
    CANCELLED;

    public Set<RequisitionStatus> allowedNextStatuses() {
        return switch (this) {
            case DRAFT -> Set.of(SUBMITTED, CANCELLED);
            case SUBMITTED -> Set.of(APPROVED, REJECTED, CANCELLED);
            // CLOSED straight from APPROVED covers a requirement that was
            // accepted and then dropped before any sourcing happened.
            case APPROVED -> Set.of(IN_PROGRESS, CLOSED, CANCELLED);
            case IN_PROGRESS -> Set.of(PARTIALLY_FULFILLED, FULFILLED, CLOSED, CANCELLED);
            // Can still be cancelled: a client may pull a requirement with
            // three of ten posts filled, and those deployments get released.
            case PARTIALLY_FULFILLED -> Set.of(FULFILLED, CLOSED, CANCELLED);
            case FULFILLED -> Set.of(PARTIALLY_FULFILLED, CLOSED);
            case CLOSED, REJECTED, CANCELLED -> Set.of();
        };
    }

    public boolean canTransitionTo(RequisitionStatus target) {
        return target != null && allowedNextStatuses().contains(target);
    }

    public boolean isTerminal() {
        return this == CLOSED || this == REJECTED || this == CANCELLED;
    }

    /** Whether the company has committed to supplying against this requisition. */
    public boolean isCommitted() {
        return this == APPROVED || this == IN_PROGRESS
                || this == PARTIALLY_FULFILLED || this == FULFILLED;
    }

    /** Whether workers may be deployed against it now. */
    public boolean acceptsDeployments() {
        return isCommitted();
    }

    /** Whether the client can still edit the request itself. */
    public boolean isEditableByClient() {
        return this == DRAFT;
    }

    /**
     * What to tell the client.
     *
     * <p>{@code IN_PROGRESS} tells them nothing useful, and
     * {@code PARTIALLY_FULFILLED} reads like a failure when it is progress.
     */
    public String clientFacingLabel() {
        return switch (this) {
            case DRAFT -> "Draft - not yet submitted";
            case SUBMITTED -> "Submitted - awaiting confirmation";
            case APPROVED -> "Confirmed - sourcing has started";
            case IN_PROGRESS -> "Sourcing in progress";
            case PARTIALLY_FULFILLED -> "Partly deployed - the rest is on the way";
            case FULFILLED -> "Fully deployed";
            case CLOSED -> "Closed";
            case REJECTED -> "Could not be accepted";
            case CANCELLED -> "Cancelled";
        };
    }
}
