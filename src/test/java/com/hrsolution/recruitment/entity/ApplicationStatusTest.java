package com.hrsolution.recruitment.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The hiring funnel's state machine.
 *
 * <p>Tested exhaustively rather than by example because this enum is the only
 * thing standing between the pipeline and a candidate appearing on a client
 * site with no record of having been screened. Four callers drive these
 * transitions - the public apply form, the candidate portal, the recruiter
 * board and the interview result handler - so a gap here is a gap everywhere.
 */
class ApplicationStatusTest {

    @Nested
    @DisplayName("illegal transitions")
    class IllegalTransitions {

        @Test
        @DisplayName("APPLIED cannot jump straight to JOINED")
        void cannotSkipTheFunnel() {
            // Would put someone on a client site with no screening and no
            // interview on record. Phase 6 converts JOINED into a worker, and
            // Phase 10 has to show the principal employer they were vetted.
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.JOINED))
                    .isFalse();
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.OFFERED))
                    .isFalse();
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(ApplicationStatus.SELECTED))
                    .isFalse();
            assertThat(ApplicationStatus.APPLIED
                    .canTransitionTo(ApplicationStatus.INTERVIEW_SCHEDULED)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationStatus.class, names = {"JOINED", "REJECTED", "WITHDRAWN"})
        @DisplayName("nothing moves out of a terminal status")
        void terminalStatusesAreFinal(ApplicationStatus terminal) {
            // Letting a rejection be reversed in place would erase the fact
            // that it happened. Re-engaging a candidate is a new application,
            // which stays visible in their history.
            assertThat(terminal.allowedNextStatuses()).isEmpty();

            for (ApplicationStatus target : ApplicationStatus.values()) {
                assertThat(terminal.canTransitionTo(target))
                        .as("%s must not move to %s", terminal, target)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("an offered candidate cannot be put back on hold")
        void cannotParkAnOfferedCandidate() {
            // An offer is a commercial commitment. Quietly parking it would
            // leave the candidate waiting on an offer the business thinks it
            // has withdrawn.
            assertThat(ApplicationStatus.OFFERED.canTransitionTo(ApplicationStatus.ON_HOLD))
                    .isFalse();
            assertThat(ApplicationStatus.OFFERED.canTransitionTo(ApplicationStatus.SCREENING))
                    .isFalse();
        }

        @Test
        @DisplayName("a null target is never allowed")
        void nullTargetIsRejected() {
            assertThat(ApplicationStatus.APPLIED.canTransitionTo(null)).isFalse();
        }

        @ParameterizedTest
        @EnumSource(ApplicationStatus.class)
        @DisplayName("no status allows a transition to itself")
        void noSelfTransitions(ApplicationStatus status) {
            // Application.moveTo short-circuits a no-op move, so a self
            // transition listed here would be dead configuration that implies
            // a trail entry nobody writes.
            assertThat(status.allowedNextStatuses()).doesNotContain(status);
        }
    }

    @Nested
    @DisplayName("the happy path")
    class HappyPath {

        @Test
        @DisplayName("the full route from applied to joined is walkable")
        void fullRouteIsWalkable() {
            ApplicationStatus[] route = {
                    ApplicationStatus.APPLIED,
                    ApplicationStatus.SCREENING,
                    ApplicationStatus.SHORTLISTED,
                    ApplicationStatus.INTERVIEW_SCHEDULED,
                    ApplicationStatus.SELECTED,
                    ApplicationStatus.OFFERED,
                    ApplicationStatus.JOINED,
            };

            for (int i = 0; i < route.length - 1; i++) {
                assertThat(route[i].canTransitionTo(route[i + 1]))
                        .as("%s -> %s is the happy path and must be allowed",
                                route[i], route[i + 1])
                        .isTrue();
            }
        }

        @Test
        @DisplayName("an interview can lead straight to SELECTED")
        void interviewCanDecideDirectly() {
            // Usual for operative roles, where the interview is the decision.
            assertThat(ApplicationStatus.INTERVIEW_SCHEDULED
                    .canTransitionTo(ApplicationStatus.SELECTED)).isTrue();
            assertThat(ApplicationStatus.SHORTLISTED
                    .canTransitionTo(ApplicationStatus.SELECTED)).isTrue();
        }

        @Test
        @DisplayName("SELECTED can go straight to JOINED without a formal offer step")
        void offerStepIsOptional() {
            assertThat(ApplicationStatus.SELECTED.canTransitionTo(ApplicationStatus.JOINED))
                    .isTrue();
        }

        @ParameterizedTest
        @EnumSource(value = ApplicationStatus.class,
                names = {"APPLIED", "SCREENING", "SHORTLISTED", "INTERVIEW_SCHEDULED",
                        "SELECTED", "OFFERED", "ON_HOLD"})
        @DisplayName("every live status can be rejected or withdrawn")
        void everyLiveStatusCanEnd(ApplicationStatus status) {
            // A candidate must always be able to pull out, and the business
            // must always be able to say no. A status that trapped an
            // application would need a database fix to resolve.
            assertThat(status.canTransitionTo(ApplicationStatus.REJECTED))
                    .as("%s must be rejectable", status).isTrue();
            assertThat(status.canTransitionTo(ApplicationStatus.WITHDRAWN))
                    .as("%s must be withdrawable", status).isTrue();
        }
    }

    @Nested
    @DisplayName("ON_HOLD")
    class OnHold {

        @Test
        @DisplayName("is not terminal, and can rejoin the funnel")
        void parkedCandidatesComeBack() {
            // The one status that exists to be left and returned from.
            assertThat(ApplicationStatus.ON_HOLD.isTerminal()).isFalse();
            assertThat(ApplicationStatus.ON_HOLD.allowedNextStatuses())
                    .contains(ApplicationStatus.SCREENING,
                            ApplicationStatus.SHORTLISTED,
                            ApplicationStatus.INTERVIEW_SCHEDULED,
                            ApplicationStatus.SELECTED);
        }

        @Test
        @DisplayName("cannot jump from parked straight to joined")
        void stillCannotSkipTheFunnel() {
            assertThat(ApplicationStatus.ON_HOLD.canTransitionTo(ApplicationStatus.JOINED))
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("derived flags")
    class DerivedFlags {

        @Test
        @DisplayName("terminal and open are exact opposites")
        void terminalAndOpenAgree() {
            for (ApplicationStatus status : ApplicationStatus.values()) {
                assertThat(status.isOpen()).isNotEqualTo(status.isTerminal());
            }
        }

        @Test
        @DisplayName("only SELECTED, OFFERED and JOINED consume an opening")
        void commitmentsConsumeOpenings() {
            // A job with three openings and three selected candidates is full
            // even though nobody has joined yet - so the count has to include
            // the commitments, not just the arrivals.
            for (ApplicationStatus status : ApplicationStatus.values()) {
                boolean expected = status == ApplicationStatus.SELECTED
                        || status == ApplicationStatus.OFFERED
                        || status == ApplicationStatus.JOINED;
                assertThat(status.consumesAnOpening())
                        .as("%s", status)
                        .isEqualTo(expected);
            }
        }

        @Test
        @DisplayName("only JOINED counts as hired")
        void onlyJoinedIsHired() {
            // Phase 6 keys the worker conversion off this, so SELECTED and
            // OFFERED must not qualify.
            assertThat(ApplicationStatus.JOINED.isHired()).isTrue();
            assertThat(ApplicationStatus.SELECTED.isHired()).isFalse();
            assertThat(ApplicationStatus.OFFERED.isHired()).isFalse();
        }

        @Test
        @DisplayName("post-offer covers OFFERED and JOINED only")
        void postOfferIsTheCommitmentBoundary() {
            assertThat(ApplicationStatus.OFFERED.isPostOffer()).isTrue();
            assertThat(ApplicationStatus.JOINED.isPostOffer()).isTrue();
            assertThat(ApplicationStatus.SELECTED.isPostOffer()).isFalse();
        }
    }

    @Nested
    @DisplayName("candidate-facing labels")
    class CandidateLabels {

        @ParameterizedTest
        @EnumSource(ApplicationStatus.class)
        @DisplayName("every status has a label that is not just the enum name")
        void everyStatusReadsAsEnglish(ApplicationStatus status) {
            // These go on the candidate's own portal. "SCREENING" tells an
            // applicant nothing, and "ON_HOLD" reads as a rejection when it is
            // the opposite.
            String label = status.candidateFacingLabel();

            assertThat(label).isNotBlank();
            assertThat(label).isNotEqualTo(status.name());
            assertThat(label).doesNotContain("_");
        }

        @Test
        @DisplayName("ON_HOLD does not read as a rejection")
        void parkedDoesNotSoundLikeNo() {
            assertThat(ApplicationStatus.ON_HOLD.candidateFacingLabel())
                    .isEqualTo("Kept on file for a future opening");
        }
    }
}
