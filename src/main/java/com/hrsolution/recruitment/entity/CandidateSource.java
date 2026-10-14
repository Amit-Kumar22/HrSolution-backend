package com.hrsolution.recruitment.entity;

/**
 * How a candidate reached us.
 *
 * <p>The one field in the recruitment module that exists purely for reporting:
 * cost per hire by channel is the number that decides next quarter's sourcing
 * spend, and it cannot be reconstructed later from anything else.
 */
public enum CandidateSource {

    /** Applied through the public website. */
    WEBSITE,

    /** Referred by an existing worker or client. */
    REFERRAL,

    /** Turned up at the office. Common for operative roles. */
    WALK_IN,

    /** Supplied by a staffing agency or consultant. */
    AGENCY,

    /** Hired at a college or ITI drive. */
    CAMPUS,

    OTHER
}
