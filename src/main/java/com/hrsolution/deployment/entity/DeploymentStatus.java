package com.hrsolution.deployment.entity;

/**
 * Lifecycle of a worker's posting to a client site.
 *
 * <p>Only {@link #ACTIVE} occupies the worker. The three end states are kept
 * distinct rather than collapsed into one "ended" value because they answer
 * different questions a client or an inspector will ask: did the worker move
 * elsewhere, did we pull them, or did the requirement simply run its course.
 */
public enum DeploymentStatus {

    /** Currently posted. Counts against the worker's availability. */
    ACTIVE,

    /** Moved to another site or client. Ended by a successor deployment. */
    TRANSFERRED,

    /** Pulled out before the end date - by us, the client, or the worker. */
    RELEASED,

    /** Ran to its end date. */
    COMPLETED;

    /** Whether this posting still occupies the worker. */
    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isEnded() {
        return !isActive();
    }

    /** Whether this posting should be billed for the period it covers. */
    public boolean isBillable() {
        // Every state is billable for the days actually worked - ending a
        // posting does not undo the work. Phase 9 bills on attendance, and
        // this only decides whether the deployment is still accruing.
        return true;
    }
}
