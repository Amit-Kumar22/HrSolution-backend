package com.hrsolution.worker.entity;

/**
 * Whether a worker's police verification has been obtained.
 *
 * <p>Not administrative detail. Under the Private Security Agencies
 * (Regulation) Act a security guard may not be deployed without it, so
 * {@code DeploymentService} refuses to post a guard whose verification has not
 * come back - the licence, and the client's own liability, depend on it.
 */
public enum PoliceVerificationStatus {

    /** Nobody has started it. The default for a new worker. */
    NOT_STARTED,

    /** Submitted to the police, awaiting the response. */
    IN_PROGRESS,

    /** Came back clear. */
    VERIFIED,

    /** Came back adverse. Blocks deployment to security roles permanently. */
    FAILED,

    /**
     * Deliberately exempted - a role where no statute requires it, such as
     * housekeeping at a non-sensitive site.
     *
     * <p>A conscious decision by a manager, not a default, which is why it is
     * distinct from {@link #NOT_STARTED}.
     */
    NOT_REQUIRED;

    /** Whether this worker may be deployed to a role that requires verification. */
    public boolean permitsSecurityDeployment() {
        return this == VERIFIED || this == NOT_REQUIRED;
    }

    /** Whether somebody still has to act on this. */
    public boolean isOutstanding() {
        return this == NOT_STARTED || this == IN_PROGRESS;
    }
}
