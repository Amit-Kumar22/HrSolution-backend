package com.hrsolution.recruitment.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.time.Instant;

/**
 * A scheduled conversation with a candidate about one application.
 *
 * <p>Unique on {@code (application_id, round_number)} so two rows cannot both
 * claim to be round two. Rounds are explicit rather than implied by ordering
 * because an interview can be rescheduled, and "the second row by date" stops
 * being "the second round" the moment that happens.
 *
 * <p>{@link #result} starts {@code PENDING} and is the field that drives the
 * application forward: {@code RecruitmentService} reads a {@code PASS} as
 * grounds to shortlist or select, and a {@code NO_SHOW} is tracked separately
 * from a {@code FAIL} because it measures the confirmation process rather than
 * the candidate.
 */
@Entity
@Table(name = "interviews")
@Getter
@Setter
public class Interview extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "round_number", nullable = false)
    private int roundNumber = 1;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", nullable = false, length = 20)
    private InterviewMode mode = InterviewMode.IN_PERSON;

    /** An address for IN_PERSON and PHONE, a joining URL for VIDEO. */
    @Column(name = "venue_or_link", length = 400)
    private String venueOrLink;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interviewer_user_id")
    private User interviewer;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 20)
    private InterviewResult result = InterviewResult.PENDING;

    /** 1-5. Constrained in the database too, so a bad client cannot store 11. */
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "feedback", length = 2000)
    private String feedback;

    // ------------------------------------------------------------------
    // Domain behaviour
    // ------------------------------------------------------------------

    /** Whether this interview is still ahead of us. */
    public boolean isUpcomingAt(Instant now) {
        return scheduledAt != null && now != null && scheduledAt.isAfter(now);
    }

    /**
     * Whether the slot has passed with no outcome recorded.
     *
     * <p>The number a recruitment dashboard leads with: an interview nobody
     * wrote up is a candidate left waiting, which is how good applicants are
     * lost.
     */
    public boolean isAwaitingFeedback(Instant now) {
        return !result.isDecided() && scheduledAt != null && now != null
                && !scheduledAt.isAfter(now);
    }

    /** When this interview is expected to end, for conflict checks. */
    public Instant endsAt() {
        if (scheduledAt == null) {
            return null;
        }
        int minutes = durationMinutes == null ? DEFAULT_DURATION_MINUTES : durationMinutes;
        return scheduledAt.plus(Duration.ofMinutes(Math.max(1, minutes)));
    }

    /**
     * Whether this interview's slot overlaps another's.
     *
     * <p>Used to warn about double-booking an interviewer rather than to refuse
     * it - a panel sitting in on two candidates back to back with a slight
     * overlap is a scheduling decision, not an error.
     */
    public boolean overlaps(Interview other) {
        if (other == null || scheduledAt == null || other.getScheduledAt() == null) {
            return false;
        }
        return scheduledAt.isBefore(other.endsAt()) && other.getScheduledAt().isBefore(endsAt());
    }

    /** Assumed slot length when none is given. */
    public static final int DEFAULT_DURATION_MINUTES = 30;
}
