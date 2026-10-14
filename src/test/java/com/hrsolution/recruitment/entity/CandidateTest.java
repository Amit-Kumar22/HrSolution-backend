package com.hrsolution.recruitment.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Consent, email masking and the experience label on a candidate. */
class CandidateTest {

    private Candidate candidate;

    @BeforeEach
    void setUp() {
        candidate = new Candidate();
        candidate.setFullName("Rakesh Shinde");
        candidate.setEmail("rakesh.shinde@example.com");
    }

    @Nested
    @DisplayName("consent")
    class Consent {

        @Test
        @DisplayName("stamps the time when consent is first given")
        void firstConsentIsStamped() {
            assertThat(candidate.isConsentGiven()).isFalse();
            assertThat(candidate.getConsentGivenAt()).isNull();

            candidate.giveConsent();

            assertThat(candidate.isConsentGiven()).isTrue();
            assertThat(candidate.getConsentGivenAt()).isNotNull();
        }

        @Test
        @DisplayName("does not move the timestamp on a repeat call")
        void repeatConsentKeepsTheOriginalDate() {
            // The DPDP record is when consent was FIRST given. Re-stamping it
            // on every profile save would destroy the only date that matters.
            candidate.giveConsent();
            var original = candidate.getConsentGivenAt();

            candidate.giveConsent();

            assertThat(candidate.getConsentGivenAt()).isEqualTo(original);
        }
    }

    @Nested
    @DisplayName("email masking")
    class Masking {

        @Test
        @DisplayName("keeps the first and last character of the local part")
        void masksTheMiddle() {
            // Used in the duplicate-application message returned to an
            // anonymous caller: enough for the real owner to recognise, not
            // enough to confirm the address to someone guessing.
            assertThat(candidate.maskedEmail()).isEqualTo("r***********e@example.com");
        }

        @Test
        @DisplayName("masks a very short local part entirely")
        void shortLocalPartIsFullyMasked() {
            assertThat(Candidate.maskEmail("ab@example.com")).isEqualTo("**@example.com");
            assertThat(Candidate.maskEmail("a@example.com")).isEqualTo("*@example.com");
        }

        @Test
        @DisplayName("returns null rather than leaking a malformed value")
        void malformedInputIsNull() {
            assertThat(Candidate.maskEmail(null)).isNull();
            assertThat(Candidate.maskEmail("not-an-email")).isNull();
        }

        @Test
        @DisplayName("never contains the middle of the original local part")
        void nothingLeaksThroughTheMask() {
            String masked = Candidate.maskEmail("rakesh.shinde@example.com");
            assertThat(masked).doesNotContain("akesh.shind");
        }
    }

    @Nested
    @DisplayName("experience label")
    class ExperienceLabel {

        @Test
        @DisplayName("null when unstated, Fresher at zero")
        void edges() {
            assertThat(candidate.experienceLabel()).isNull();

            candidate.setTotalExperienceMonths(0);
            assertThat(candidate.experienceLabel()).isEqualTo("Fresher");
        }

        @Test
        @DisplayName("renders months, years, and years with months")
        void renders() {
            candidate.setTotalExperienceMonths(1);
            assertThat(candidate.experienceLabel()).isEqualTo("1 month");

            candidate.setTotalExperienceMonths(7);
            assertThat(candidate.experienceLabel()).isEqualTo("7 months");

            candidate.setTotalExperienceMonths(12);
            assertThat(candidate.experienceLabel()).isEqualTo("1 year");

            candidate.setTotalExperienceMonths(42);
            assertThat(candidate.experienceLabel()).isEqualTo("3 years 6 months");
        }
    }

    @Nested
    @DisplayName("spam flagging")
    class SpamFlagging {

        @Test
        @DisplayName("flagging records a reason, clearing removes both")
        void flagAndClear() {
            candidate.flagAsSpam("Honeypot field was filled");
            assertThat(candidate.isSpamSuspected()).isTrue();
            assertThat(candidate.getSpamReason()).isEqualTo("Honeypot field was filled");

            // Clearing is how a false positive is rescued, so it must leave no
            // residue that a later filter could trip over.
            candidate.clearSpamFlag();
            assertThat(candidate.isSpamSuspected()).isFalse();
            assertThat(candidate.getSpamReason()).isNull();
        }
    }

    @Nested
    @DisplayName("login linkage")
    class LoginLinkage {

        @Test
        @DisplayName("a guest candidate has no login")
        void guestHasNoLogin() {
            assertThat(candidate.hasLogin()).isFalse();
            assertThat(candidate.hasResume()).isFalse();
        }
    }
}
