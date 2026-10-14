package com.hrsolution.recruitment.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Application#moveTo} - the single gate every status change passes
 * through.
 *
 * <p>The pairing of "validate the move" and "append to the trail" in one method
 * is the point of these tests. If a future change splits them, there is a path
 * that records a move without checking it, or checks one without recording it,
 * and a funnel history with gaps is not evidence of anything.
 */
class ApplicationTest {

    private Application application;

    @BeforeEach
    void setUp() {
        Candidate candidate = new Candidate();
        candidate.setFullName("Rakesh Shinde");
        candidate.setEmail("rakesh@example.com");

        Job job = new Job();
        job.setTitle("Security Guard");
        job.setOpenings(3);

        application = new Application();
        application.setCandidate(candidate);
        application.setJob(job);
        application.setAppliedOn(LocalDate.of(2026, 10, 1));
        application.setStatus(ApplicationStatus.APPLIED);
    }

    @Nested
    @DisplayName("moving through the funnel")
    class Moving {

        @Test
        @DisplayName("a legal move changes the status and appends to the trail")
        void legalMoveIsRecorded() {
            boolean changed = application.moveTo(
                    ApplicationStatus.SCREENING, 7L, "hr@example.com", "Reading the CV");

            assertThat(changed).isTrue();
            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.SCREENING);
            assertThat(application.getStatusHistory()).hasSize(1);

            ApplicationStatusHistory entry = application.getStatusHistory().getFirst();
            assertThat(entry.getFromStatus()).isEqualTo(ApplicationStatus.APPLIED);
            assertThat(entry.getToStatus()).isEqualTo(ApplicationStatus.SCREENING);
            assertThat(entry.getChangedByUserId()).isEqualTo(7L);
            assertThat(entry.getChangedByEmail()).isEqualTo("hr@example.com");
            assertThat(entry.getNotes()).isEqualTo("Reading the CV");
            assertThat(entry.madeByStaff()).isTrue();
            assertThat(entry.isOrigin()).isFalse();
        }

        @Test
        @DisplayName("an illegal move throws and changes nothing")
        void illegalMoveLeavesNoTrace() {
            assertThatThrownBy(() -> application.moveTo(
                    ApplicationStatus.JOINED, 7L, "hr@example.com", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("APPLIED")
                    .hasMessageContaining("JOINED");

            // The important half: a refused move must not leave a half-written
            // trail suggesting it happened.
            assertThat(application.getStatus()).isEqualTo(ApplicationStatus.APPLIED);
            assertThat(application.getStatusHistory()).isEmpty();
        }

        @Test
        @DisplayName("moving to the status it already has is a no-op, not an error")
        void repeatedMoveIsIgnored() {
            // Two recruiters clicking the same button should not produce two
            // identical trail entries.
            boolean changed = application.moveTo(
                    ApplicationStatus.APPLIED, 7L, "hr@example.com", null);

            assertThat(changed).isFalse();
            assertThat(application.getStatusHistory()).isEmpty();
        }

        @Test
        @DisplayName("a null target is refused")
        void nullTargetThrows() {
            assertThatThrownBy(() -> application.moveTo(null, 7L, "hr@example.com", null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("target status is required");
        }

        @Test
        @DisplayName("a candidate acting on their own application records no staff id")
        void candidateActionsAreDistinguishable() {
            application.moveTo(ApplicationStatus.WITHDRAWN, null,
                    "rakesh@example.com", "Accepted another offer");

            ApplicationStatusHistory entry = application.getStatusHistory().getFirst();
            assertThat(entry.getChangedByUserId()).isNull();
            assertThat(entry.madeByStaff()).isFalse();
            assertThat(entry.getChangedByEmail()).isEqualTo("rakesh@example.com");
        }

        @Test
        @DisplayName("the whole route accumulates one trail entry per move")
        void trailAccumulates() {
            application.moveTo(ApplicationStatus.SCREENING, 7L, "hr@example.com", null);
            application.moveTo(ApplicationStatus.SHORTLISTED, 7L, "hr@example.com", null);
            application.moveTo(ApplicationStatus.SELECTED, 7L, "hr@example.com", null);
            application.moveTo(ApplicationStatus.OFFERED, 7L, "hr@example.com", null);
            application.moveTo(ApplicationStatus.JOINED, 7L, "hr@example.com", null);

            assertThat(application.getStatusHistory()).hasSize(5);
            assertThat(application.getStatusHistory())
                    .extracting(ApplicationStatusHistory::getToStatus)
                    .containsExactly(ApplicationStatus.SCREENING, ApplicationStatus.SHORTLISTED,
                            ApplicationStatus.SELECTED, ApplicationStatus.OFFERED,
                            ApplicationStatus.JOINED);
        }
    }

    @Nested
    @DisplayName("the submission entry")
    class Submission {

        @Test
        @DisplayName("recordSubmission writes an origin row with a null fromStatus")
        void originRowHasNoFromStatus() {
            application.recordSubmission(null, "rakesh@example.com", "Submitted via the form");

            ApplicationStatusHistory entry = application.getStatusHistory().getFirst();
            assertThat(entry.isOrigin()).isTrue();
            assertThat(entry.getFromStatus()).isNull();
            assertThat(entry.getToStatus()).isEqualTo(ApplicationStatus.APPLIED);
        }
    }

    @Nested
    @DisplayName("the rejection reason")
    class RejectionReason {

        @Test
        @DisplayName("is cleared when the application leaves REJECTED")
        void staleReasonIsCleared() {
            // Unreachable today, since REJECTED is terminal. Guarded anyway: if
            // a future change makes it reversible, a stale reason sitting on an
            // active application would contradict its status on screen.
            application.setRejectionReason("Not enough experience");
            application.moveTo(ApplicationStatus.SCREENING, 7L, "hr@example.com", null);

            assertThat(application.getRejectionReason()).isNull();
        }
    }

    @Nested
    @DisplayName("derived values")
    class Derived {

        @Test
        @DisplayName("days in pipeline counts from the application date")
        void ageIsCounted() {
            assertThat(application.daysInPipeline(LocalDate.of(2026, 10, 15))).isEqualTo(14);
        }

        @Test
        @DisplayName("days in pipeline never goes negative")
        void ageIsNeverNegative() {
            // A clock skew or a back-dated application must not render as
            // "-3 days" on the board.
            assertThat(application.daysInPipeline(LocalDate.of(2026, 9, 1))).isZero();
        }

        @Test
        @DisplayName("days in pipeline is zero when either date is missing")
        void ageHandlesNulls() {
            application.setAppliedOn(null);
            assertThat(application.daysInPipeline(LocalDate.of(2026, 10, 15))).isZero();
        }

        @Test
        @DisplayName("interview scheduling is only accepted mid-funnel")
        void schedulingWindow() {
            application.setStatus(ApplicationStatus.APPLIED);
            assertThat(application.acceptsInterviewScheduling()).isFalse();

            application.setStatus(ApplicationStatus.SCREENING);
            assertThat(application.acceptsInterviewScheduling()).isTrue();

            application.setStatus(ApplicationStatus.SHORTLISTED);
            assertThat(application.acceptsInterviewScheduling()).isTrue();

            application.setStatus(ApplicationStatus.JOINED);
            assertThat(application.acceptsInterviewScheduling()).isFalse();
        }

        @Test
        @DisplayName("the effective resume falls back to the candidate's current CV")
        void resumeFallsBack() {
            // An application submitted before the candidate had a CV on file
            // should still show the one they uploaded later, rather than
            // nothing at all.
            assertThat(application.effectiveResume()).isNull();

            application.getCandidate().setResumeDocument(
                    new com.hrsolution.document.entity.StoredDocument());
            assertThat(application.effectiveResume()).isNotNull();
        }
    }
}
