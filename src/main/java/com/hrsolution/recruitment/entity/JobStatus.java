package com.hrsolution.recruitment.entity;

/**
 * Lifecycle of a job advert.
 *
 * <p>A status enum rather than the {@code published} boolean used by service
 * pages and testimonials in Phase 3. Content either is or is not on the site; a
 * vacancy has a life of its own, and the distinction between "we stopped
 * advertising because we filled it" and "we stopped because the client pulled
 * the requirement" is exactly the thing a recruitment report is asked for. A
 * boolean cannot carry it.
 */
public enum JobStatus {

    /** Being written. Never visible publicly. */
    DRAFT,

    /** Advertised and accepting applications. */
    OPEN,

    /**
     * Temporarily not accepting applications.
     *
     * <p>Hidden from the public list like a draft, but existing applications
     * stay live - the client has paused, not cancelled.
     */
    ON_HOLD,

    /** Withdrawn without hiring anyone. */
    CLOSED,

    /** All openings taken. */
    FILLED;

    /** Whether the advert should appear on the public site, expiry aside. */
    public boolean isPubliclyVisible() {
        return this == OPEN;
    }

    /** Whether new applications may be submitted against this job. */
    public boolean acceptsApplications() {
        return this == OPEN;
    }

    /** Whether the job is done with, so its applications should be resolved. */
    public boolean isFinished() {
        return this == CLOSED || this == FILLED;
    }
}
