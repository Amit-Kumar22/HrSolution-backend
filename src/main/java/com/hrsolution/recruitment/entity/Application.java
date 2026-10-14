package com.hrsolution.recruitment.entity;

import com.hrsolution.common.domain.SoftDeletableEntity;
import com.hrsolution.document.entity.StoredDocument;
import com.hrsolution.user.entity.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * One candidate against one job.
 *
 * <p>Unique on {@code (job_id, candidate_id)}: a second attempt at the same
 * vacancy is a {@code 409}, not another row, so the pipeline counts a recruiter
 * works from stay truthful.
 *
 * <h2>Status changes go through the entity</h2>
 *
 * <p>{@link #moveTo} is the only way the status changes, and it appends to
 * {@link #statusHistory} as it goes. That is deliberate: four different callers
 * drive this funnel - the public form, the candidate portal, the recruiter
 * board and the interview result handler - and a transition rule enforced in
 * one service is a rule the other three can bypass. Putting both the check and
 * the trail write here means there is no path that records a move without
 * validating it, or validates one without recording it.
 *
 * <p>The history is {@code cascade = ALL} with no orphan removal because it is
 * append-only; nothing ever detaches a row from it.
 */
@Entity
@Table(name = "applications")
@Getter
@Setter
public class Application extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApplicationStatus status = ApplicationStatus.APPLIED;

    @Column(name = "applied_on", nullable = false)
    private LocalDate appliedOn;

    /**
     * How this application arrived.
     *
     * <p>Separate from the candidate's own source: someone first met at a
     * campus drive may later apply through the website, and the channel that
     * produced <em>this</em> application is what a cost-per-hire report needs.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private CandidateSource source = CandidateSource.WEBSITE;

    @Column(name = "cover_note", length = 2000)
    private String coverNote;

    @Column(name = "expected_salary", precision = 12, scale = 2)
    private BigDecimal expectedSalary;

    /**
     * The CV submitted with this application.
     *
     * <p>A snapshot, not a pointer to the candidate's current file. A recruiter
     * reopening a two-year-old application sees what they were actually sent,
     * and an anonymous submission can never overwrite a registered candidate's
     * own resume.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_document_id")
    private StoredDocument resumeDocument;

    /** Owning recruiter. Null shows on the board as needing attention. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    /** Only meaningful in REJECTED. Readable without walking the trail. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("createdAt ASC, id ASC")
    private List<ApplicationStatusHistory> statusHistory = new ArrayList<>();

    // ------------------------------------------------------------------
    // Domain behaviour
    // ------------------------------------------------------------------

    /**
     * Records the creation of this application as the first history row.
     *
     * <p>Called once, by the service, immediately after the application is
     * populated. {@code fromStatus} is null, which is what marks the row as the
     * origin rather than a move.
     */
    public void recordSubmission(Long actorUserId, String actorEmail, String note) {
        appendHistory(null, status, actorUserId, actorEmail, note);
    }

    /**
     * Moves to {@code target}, appending to the trail.
     *
     * @return true if the status changed, false if it was already {@code target}
     * @throws IllegalStateException if the move is not allowed. The service
     *         translates this into a {@code BusinessRuleException}; the entity
     *         does not depend on the web error types.
     */
    public boolean moveTo(ApplicationStatus target,
                          Long actorUserId,
                          String actorEmail,
                          String note) {
        if (target == null) {
            throw new IllegalStateException("A target status is required.");
        }
        if (target == status) {
            return false;
        }
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                    "An application cannot move from %s to %s.".formatted(status, target));
        }

        ApplicationStatus previous = status;
        this.status = target;

        // Leaving REJECTED is impossible, so a reason can only ever be stale if
        // the application went ON_HOLD and came back. Clear it so the field
        // never contradicts the status.
        if (target != ApplicationStatus.REJECTED) {
            this.rejectionReason = null;
        }

        appendHistory(previous, target, actorUserId, actorEmail, note);
        return true;
    }

    private void appendHistory(ApplicationStatus from,
                               ApplicationStatus to,
                               Long actorUserId,
                               String actorEmail,
                               String note) {
        ApplicationStatusHistory entry = new ApplicationStatusHistory();
        entry.setApplication(this);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedByUserId(actorUserId);
        entry.setChangedByEmail(actorEmail);
        entry.setNotes(note);
        statusHistory.add(entry);
    }

    /** Whether an interview may be scheduled against this application now. */
    public boolean acceptsInterviewScheduling() {
        return status == ApplicationStatus.SHORTLISTED
                || status == ApplicationStatus.INTERVIEW_SCHEDULED
                || status == ApplicationStatus.SCREENING;
    }

    public boolean isAssigned() {
        return assignedTo != null;
    }

    /** Days this application has been open, for the ageing column on the board. */
    public long daysInPipeline(LocalDate asOf) {
        if (appliedOn == null || asOf == null) {
            return 0;
        }
        return Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(appliedOn, asOf));
    }

    /** The resume to show: this application's snapshot, else the candidate's current one. */
    public StoredDocument effectiveResume() {
        if (resumeDocument != null) {
            return resumeDocument;
        }
        return candidate == null ? null : candidate.getResumeDocument();
    }
}
