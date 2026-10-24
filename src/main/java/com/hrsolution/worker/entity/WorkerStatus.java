package com.hrsolution.worker.entity;

/**
 * Where a worker stands with the company.
 *
 * <p>Note there is no {@code ACTIVE}: it would overlap {@link #ON_BENCH} and
 * {@link #DEPLOYED} without distinguishing them, and "how many people are on
 * the bench" is the number operations manages the business by. A worker is
 * either earning on a client site or costing money waiting.
 *
 * <p>{@link #ON_BENCH} and {@link #DEPLOYED} are maintained by
 * {@code DeploymentService} rather than set by hand - they are a projection of
 * the deployments table, and letting them be edited directly would let the
 * status disagree with where the person actually is.
 */
public enum WorkerStatus {

    /** On the payroll, not currently posted anywhere. */
    ON_BENCH,

    /** Posted to a client site. Set by an active deployment. */
    DEPLOYED,

    /** Away on approved leave. Phase 7 drives this. */
    ON_LEAVE,

    /** Left of their own accord. Terminal. */
    RESIGNED,

    /** Dismissed. Terminal. */
    TERMINATED;

    /** Whether this worker is still employed. */
    public boolean isEmployed() {
        return this != RESIGNED && this != TERMINATED;
    }

    /** Whether the worker has left, however they left. */
    public boolean hasExited() {
        return !isEmployed();
    }

    /** Whether a new deployment may be created for this worker. */
    public boolean isDeployable() {
        // ON_LEAVE is excluded deliberately: posting someone who is on
        // approved leave produces an attendance sheet nobody can fill in.
        return this == ON_BENCH || this == DEPLOYED;
    }

    /** Whether the status is derived from the deployments table. */
    public boolean isDerivedFromDeployments() {
        return this == ON_BENCH || this == DEPLOYED;
    }
}
