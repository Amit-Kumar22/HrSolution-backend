package com.hrsolution.recruitment.entity;

/** Outcome of a scheduled interview. */
public enum InterviewResult {

    /** Scheduled but not yet held, or held without feedback recorded. */
    PENDING,

    PASS,

    FAIL,

    /** The candidate did not turn up. Distinct from FAIL on purpose: it is not
     *  an assessment, and it is the metric that tells a recruiter their
     *  confirmation process is not working. */
    NO_SHOW;

    /** Whether a decision has been recorded. */
    public boolean isDecided() {
        return this != PENDING;
    }

    /** Whether this outcome should move the application forward. */
    public boolean isPass() {
        return this == PASS;
    }
}
