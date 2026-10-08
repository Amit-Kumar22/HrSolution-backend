package com.hrsolution.enquiry.entity;

/**
 * Where an enquiry has reached in the sales pipeline.
 *
 * <p>Linear, except for the two exits. Kept deliberately short: a public form
 * feeds this, and a pipeline with fifteen stages is one nobody updates.
 */
public enum EnquiryStatus {

    /** Just submitted. Nobody has looked at it. */
    NEW,

    /** Someone has reached out to the enquirer. */
    CONTACTED,

    /** A real requirement with budget — worth pursuing. */
    QUALIFIED,

    /** Became a client. Phase 4 links this to the client record. */
    CONVERTED,

    /** Pursued and did not convert. */
    CLOSED,

    /**
     * Junk. Kept rather than deleted so the honeypot and rate-limit tuning can
     * be judged against real traffic, and so a wrongly-flagged enquiry can be
     * recovered.
     */
    SPAM;

    /** Whether this is a terminal state that no longer needs chasing. */
    public boolean isClosed() {
        return this == CONVERTED || this == CLOSED || this == SPAM;
    }
}
