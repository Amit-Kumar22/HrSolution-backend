package com.hrsolution.recruitment.entity;

import java.util.Set;

/**
 * Where an application sits in the hiring funnel.
 *
 * <p>This is a state machine rather than a label, and the transition rules are
 * on the enum rather than in a service for a specific reason: the funnel is
 * driven from four different places - the public apply form, the candidate's
 * own portal, the recruiter's pipeline board, and the interview result handler -
 * and a rule that lives in one service is a rule the other three can bypass.
 *
 * <p>The ordering of the constants is the happy path, so
 * {@code values()} reads as the funnel and {@link #isAheadOf} can compare
 * progress by ordinal.
 *
 * <h2>Why the illegal moves matter</h2>
 *
 * <p>Two in particular are not pedantry:
 *
 * <ul>
 *   <li><strong>{@code APPLIED -> JOINED}</strong> would put someone on a client
 *       site with no screening and no interview on record. Phase 6 converts a
 *       JOINED application into a worker, and Phase 10 has to show the
 *       principal employer that the person was actually vetted.</li>
 *   <li><strong>Anything out of a terminal state</strong> would let a rejection
 *       be quietly reversed with no trace. Re-engaging a rejected candidate is
 *       a new application against the job, which is visible; editing the old
 *       one is not.</li>
 * </ul>
 */
public enum ApplicationStatus {

    /** Submitted, nobody has looked yet. */
    APPLIED,

    /** A recruiter is reading the CV and checking the basics. */
    SCREENING,

    /** Passed screening, worth interviewing. */
    SHORTLISTED,

    /** At least one interview exists in the future. */
    INTERVIEW_SCHEDULED,

    /** Interviews passed; the decision to hire has been taken internally. */
    SELECTED,

    /** An offer has gone out and we are waiting for an answer. */
    OFFERED,

    /**
     * Accepted the offer and started.
     *
     * <p>The trigger for Phase 6's candidate-to-worker conversion. Terminal
     * here because everything after joining is the worker's record, not the
     * application's.
     */
    JOINED,

    /** We said no. Terminal. */
    REJECTED,

    /**
     * Parked - a good candidate with no opening right now.
     *
     * <p>Not terminal: this is the one status that exists to be left and
     * returned from, which is why it sits outside the ordered happy path.
     */
    ON_HOLD,

    /** The candidate pulled out. Terminal. */
    WITHDRAWN;

    /**
     * The statuses reachable from this one.
     *
     * <p>Built as a method rather than a field because an enum constant cannot
     * reference its siblings in its own initialiser.
     */
    public Set<ApplicationStatus> allowedNextStatuses() {
        return switch (this) {
            case APPLIED -> Set.of(SCREENING, SHORTLISTED, REJECTED, ON_HOLD, WITHDRAWN);
            case SCREENING -> Set.of(SHORTLISTED, REJECTED, ON_HOLD, WITHDRAWN);
            case SHORTLISTED -> Set.of(INTERVIEW_SCHEDULED, SELECTED, REJECTED, ON_HOLD, WITHDRAWN);
            // SELECTED straight from an interview is normal when the interview
            // is also the decision, which it usually is for operative roles.
            //
            // SHORTLISTED is the way back: cancelling the only booked interview
            // leaves the application exactly where it was before it was booked,
            // and without this the status would keep asserting a meeting that
            // is not going to happen. Not a reversal of a decision - no
            // decision has been taken at this point.
            case INTERVIEW_SCHEDULED ->
                    Set.of(SHORTLISTED, SELECTED, REJECTED, ON_HOLD, WITHDRAWN);
            case OFFERED -> Set.of(JOINED, REJECTED, WITHDRAWN);
            case SELECTED -> Set.of(OFFERED, JOINED, REJECTED, WITHDRAWN);
            // Can go back into the funnel at any pre-offer point, because that
            // is what parking a candidate is for.
            case ON_HOLD -> Set.of(SCREENING, SHORTLISTED, INTERVIEW_SCHEDULED, SELECTED,
                    REJECTED, WITHDRAWN);
            case JOINED, REJECTED, WITHDRAWN -> Set.of();
        };
    }

    public boolean canTransitionTo(ApplicationStatus target) {
        return target != null && allowedNextStatuses().contains(target);
    }

    /** Whether this application is finished with, one way or another. */
    public boolean isTerminal() {
        return this == JOINED || this == REJECTED || this == WITHDRAWN;
    }

    /** Whether the application is still a live prospect. */
    public boolean isOpen() {
        return !isTerminal();
    }

    /** Whether this status means the candidate was hired. */
    public boolean isHired() {
        return this == JOINED;
    }

    /**
     * Whether an offer has been made or passed, i.e. the commercial commitment
     * has started. Used to decide whether the candidate may still withdraw
     * silently or whether a reason is required.
     */
    public boolean isPostOffer() {
        return this == OFFERED || this == JOINED;
    }

    /**
     * Whether this status counts against a job's openings.
     *
     * <p>SELECTED and beyond are commitments, so a job with three openings and
     * three selected candidates is full even though nobody has joined yet.
     */
    public boolean consumesAnOpening() {
        return this == SELECTED || this == OFFERED || this == JOINED;
    }

    /** Whether this status is further down the happy path than {@code other}. */
    public boolean isAheadOf(ApplicationStatus other) {
        return other != null && ordinal() < JOINED.ordinal() && other.ordinal() < JOINED.ordinal()
                && ordinal() > other.ordinal();
    }

    /**
     * What to tell the candidate.
     *
     * <p>Not the same as the enum name. {@code SCREENING} tells an applicant
     * nothing, and {@code ON_HOLD} reads as a rejection when it is the
     * opposite. These strings go on the candidate's own portal; the staff
     * console shows the raw status.
     */
    public String candidateFacingLabel() {
        return switch (this) {
            case APPLIED -> "Application received";
            case SCREENING -> "Under review";
            case SHORTLISTED -> "Shortlisted - we will be in touch about an interview";
            case INTERVIEW_SCHEDULED -> "Interview scheduled";
            case SELECTED -> "Selected - we are preparing your offer";
            case OFFERED -> "Offer sent - awaiting your response";
            case JOINED -> "Joined";
            case REJECTED -> "Not selected on this occasion";
            case ON_HOLD -> "Kept on file for a future opening";
            case WITHDRAWN -> "Withdrawn by you";
        };
    }
}
