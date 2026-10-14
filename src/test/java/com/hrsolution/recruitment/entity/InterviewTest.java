package com.hrsolution.recruitment.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Slot arithmetic and the feedback-chasing flags on an interview. */
class InterviewTest {

    private static final Instant NOON = Instant.parse("2026-11-02T12:00:00Z");

    private Interview interview;

    @BeforeEach
    void setUp() {
        interview = new Interview();
        interview.setScheduledAt(NOON);
        interview.setDurationMinutes(45);
        interview.setResult(InterviewResult.PENDING);
    }

    @Nested
    @DisplayName("timing flags")
    class Timing {

        @Test
        @DisplayName("upcoming while the slot is ahead, not once it has passed")
        void upcoming() {
            assertThat(interview.isUpcomingAt(NOON.minus(Duration.ofHours(1)))).isTrue();
            assertThat(interview.isUpcomingAt(NOON)).isFalse();
            assertThat(interview.isUpcomingAt(NOON.plus(Duration.ofHours(1)))).isFalse();
        }

        @Test
        @DisplayName("awaiting feedback once the slot has passed with no result")
        void awaitingFeedback() {
            // The number a recruitment dashboard leads with: an interview
            // nobody wrote up is a candidate left waiting.
            assertThat(interview.isAwaitingFeedback(NOON.minus(Duration.ofHours(1)))).isFalse();
            assertThat(interview.isAwaitingFeedback(NOON.plus(Duration.ofHours(1)))).isTrue();
        }

        @Test
        @DisplayName("stops awaiting feedback once an outcome is recorded")
        void decidedIsNotAwaiting() {
            interview.setResult(InterviewResult.PASS);
            assertThat(interview.isAwaitingFeedback(NOON.plus(Duration.ofHours(1)))).isFalse();
        }

        @Test
        @DisplayName("a NO_SHOW counts as decided, so it leaves the chase list")
        void noShowIsDecided() {
            // Otherwise a candidate who never turned up would sit in the
            // feedback queue forever.
            interview.setResult(InterviewResult.NO_SHOW);
            assertThat(interview.isAwaitingFeedback(NOON.plus(Duration.ofHours(1)))).isFalse();
        }
    }

    @Nested
    @DisplayName("slot length")
    class SlotLength {

        @Test
        @DisplayName("ends after the stated duration")
        void statedDuration() {
            assertThat(interview.endsAt()).isEqualTo(NOON.plus(Duration.ofMinutes(45)));
        }

        @Test
        @DisplayName("assumes the default when no duration is given")
        void defaultDuration() {
            interview.setDurationMinutes(null);
            assertThat(interview.endsAt())
                    .isEqualTo(NOON.plus(Duration.ofMinutes(Interview.DEFAULT_DURATION_MINUTES)));
        }

        @Test
        @DisplayName("treats a nonsensical duration as at least a minute")
        void zeroDurationStillHasLength() {
            // A zero-length slot would overlap nothing, silently disabling the
            // double-booking warning.
            interview.setDurationMinutes(0);
            assertThat(interview.endsAt()).isAfter(NOON);
        }
    }

    @Nested
    @DisplayName("overlap detection")
    class Overlaps {

        @Test
        @DisplayName("detects a genuine clash")
        void clash() {
            Interview other = new Interview();
            other.setScheduledAt(NOON.plus(Duration.ofMinutes(30)));
            other.setDurationMinutes(45);

            assertThat(interview.overlaps(other)).isTrue();
            assertThat(other.overlaps(interview)).as("overlap is symmetric").isTrue();
        }

        @Test
        @DisplayName("back-to-back slots do not overlap")
        void backToBack() {
            // 12:00-12:45 then 12:45-13:30 is a normal interview day, not a
            // double booking.
            Interview other = new Interview();
            other.setScheduledAt(NOON.plus(Duration.ofMinutes(45)));
            other.setDurationMinutes(45);

            assertThat(interview.overlaps(other)).isFalse();
            assertThat(other.overlaps(interview)).isFalse();
        }

        @Test
        @DisplayName("a missing slot never overlaps")
        void nullsDoNotOverlap() {
            assertThat(interview.overlaps(null)).isFalse();

            Interview unscheduled = new Interview();
            assertThat(interview.overlaps(unscheduled)).isFalse();
        }
    }

    @Nested
    @DisplayName("InterviewResult and InterviewMode")
    class Enums {

        @Test
        @DisplayName("PENDING is the only undecided result")
        void pendingIsUndecided() {
            assertThat(InterviewResult.PENDING.isDecided()).isFalse();
            assertThat(InterviewResult.PASS.isDecided()).isTrue();
            assertThat(InterviewResult.FAIL.isDecided()).isTrue();
            assertThat(InterviewResult.NO_SHOW.isDecided()).isTrue();
        }

        @Test
        @DisplayName("only PASS advances a candidate")
        void onlyPassAdvances() {
            assertThat(InterviewResult.PASS.isPass()).isTrue();
            assertThat(InterviewResult.FAIL.isPass()).isFalse();
            assertThat(InterviewResult.NO_SHOW.isPass()).isFalse();
            assertThat(InterviewResult.PENDING.isPass()).isFalse();
        }

        @Test
        @DisplayName("only VIDEO needs a joining link")
        void onlyVideoNeedsALink() {
            assertThat(InterviewMode.VIDEO.needsALink()).isTrue();
            assertThat(InterviewMode.IN_PERSON.needsALink()).isFalse();
            assertThat(InterviewMode.PHONE.needsALink()).isFalse();
        }
    }
}
