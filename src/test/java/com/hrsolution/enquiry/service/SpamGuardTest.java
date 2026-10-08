package com.hrsolution.enquiry.service;

import com.hrsolution.common.web.RequestContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpamGuardTest {

    private final SpamGuard spamGuard = new SpamGuard();
    private final RequestContext context = new RequestContext("203.0.113.9", "Mozilla/5.0", "c-1");

    private SpamGuard.Verdict assess(String honeypot, Long renderedAt, String... text) {
        return spamGuard.assess(honeypot, renderedAt, context, text);
    }

    @Test
    @DisplayName("a normal submission passes")
    void genuineSubmissionPasses() {
        SpamGuard.Verdict verdict = assess(null, System.currentTimeMillis() - 45_000,
                "We need 25 security guards for our Pune plant from December.");

        assertThat(verdict.suspected()).isFalse();
        assertThat(verdict.reason()).isNull();
    }

    @Test
    @DisplayName("a filled honeypot is flagged")
    void filledHoneypotIsFlagged() {
        // The field is hidden by CSS, so a human never sees it. Most bots fill
        // every input they find - a strong signal at zero cost to real users.
        SpamGuard.Verdict verdict = assess("http://spam.example.com", null, "Hello");

        assertThat(verdict.suspected()).isTrue();
        assertThat(verdict.reason()).contains("Honeypot");
    }

    @Test
    @DisplayName("an empty or blank honeypot is not a hit")
    void emptyHoneypotIsFine() {
        assertThat(assess("", null, "Hello").suspected()).isFalse();
        assertThat(assess("   ", null, "Hello").suspected()).isFalse();
        assertThat(assess(null, null, "Hello").suspected()).isFalse();
    }

    @Test
    @DisplayName("a submission faster than a human could type is flagged")
    void tooFastIsFlagged() {
        SpamGuard.Verdict verdict = assess(null, System.currentTimeMillis() - 200, "Hello");

        assertThat(verdict.suspected()).isTrue();
        assertThat(verdict.reason()).contains("after the form was rendered");
    }

    @Test
    @DisplayName("a missing render timestamp simply skips the timing check")
    void absentTimestampSkipsTimingCheck() {
        // The field is optional, and a client that omits it must not be
        // penalised - that would flag every caller using the API directly.
        assertThat(assess(null, null, "Hello").suspected()).isFalse();
    }

    @Test
    @DisplayName("a render timestamp in the future does not flag the submission")
    void futureTimestampIsIgnored() {
        // Means a clock skew or a forged value. Neither is a reason to throw
        // away a sales lead, so only the too-fast case counts.
        assertThat(assess(null, System.currentTimeMillis() + 60_000, "Hello").suspected())
                .isFalse();
    }

    @Test
    @DisplayName("more than two links is flagged")
    void tooManyLinksIsFlagged() {
        SpamGuard.Verdict verdict = assess(null, null,
                "Visit http://a.example http://b.example and https://c.example now");

        assertThat(verdict.suspected()).isTrue();
        assertThat(verdict.reason()).contains("links");
    }

    @Test
    @DisplayName("one or two links is acceptable")
    void aCoupleOfLinksIsFine() {
        // A genuine enquiry often cites the company website.
        assertThat(assess(null, null, "Our site is https://bharattextiles.example.com").suspected())
                .isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is flagged")
    @ValueSource(strings = {
            "Cheap SEO service for your website",
            "Buy now and make money fast",
            "Guaranteed crypto investment returns",
            "Best casino bonus"
    })
    void spamPhrasesAreFlagged(String message) {
        assertThat(assess(null, null, message).suspected()).isTrue();
    }

    @Test
    @DisplayName("matching is case-insensitive")
    void caseInsensitive() {
        assertThat(assess(null, null, "CHEAP SEO SERVICE HERE").suspected()).isTrue();
    }

    @Test
    @DisplayName("checks every supplied field, not just the first")
    void checksAllFields() {
        SpamGuard.Verdict verdict = assess(null, null,
                "A perfectly ordinary message", "Subject: buy now");

        assertThat(verdict.suspected()).isTrue();
    }

    @Test
    @DisplayName("null and blank fields are skipped without error")
    void nullFieldsAreSkipped() {
        assertThat(assess(null, null, null, "", "   ", "Genuine enquiry").suspected()).isFalse();
    }

    @Test
    @DisplayName("industry vocabulary that resembles spam is not flagged")
    void legitimateBusinessLanguagePasses() {
        // The cost of a false positive here is a lost sales lead, so the phrase
        // list has to stay narrow enough not to catch ordinary B2B wording.
        assertThat(assess(null, System.currentTimeMillis() - 30_000,
                "We are a textile manufacturer and need to outsource payroll for 400 workers. "
                        + "Please share your rates and service charge.").suspected())
                .isFalse();

        assertThat(assess(null, System.currentTimeMillis() - 30_000,
                "Looking for housekeeping staff on contract. What is your pricing?").suspected())
                .isFalse();
    }
}
