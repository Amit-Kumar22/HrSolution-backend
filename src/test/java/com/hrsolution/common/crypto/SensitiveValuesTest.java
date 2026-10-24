package com.hrsolution.common.crypto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Masking of the values that are encrypted at rest.
 *
 * <p>These are not a security boundary on their own - the boundary is the
 * {@code WORKER_SENSITIVE_READ} check that decides whether to mask at all.
 * What they guarantee is that when the answer is "mask it", no more than the
 * last four characters survive.
 */
class SensitiveValuesTest {

    @Test
    @DisplayName("an Aadhaar masks to the grouped form staff recognise")
    void masksAadhaar() {
        // XXXX-XXXX-1234 is what Indian forms and payslips show, so it reads as
        // a masked Aadhaar rather than a corrupted value.
        assertThat(SensitiveValues.maskAadhaar("234512341234")).isEqualTo("XXXX-XXXX-1234");
    }

    @Test
    @DisplayName("spaces in an Aadhaar do not change the mask")
    void ignoresAadhaarSpacing() {
        assertThat(SensitiveValues.maskAadhaar("2345 1234 1234")).isEqualTo("XXXX-XXXX-1234");
    }

    @Test
    @DisplayName("a PAN keeps its length and reveals only the last four")
    void masksPan() {
        assertThat(SensitiveValues.maskPan("AABCS1234A")).isEqualTo("XXXXXX234A");
    }

    @Test
    @DisplayName("an account number keeps its length and reveals only the last four")
    void masksAccountNumber() {
        assertThat(SensitiveValues.maskAccountNumber("123456789012"))
                .isEqualTo("XXXXXXXX9012");
    }

    @Test
    @DisplayName("a value of four characters or fewer is masked entirely")
    void shortValuesAreFullyMasked() {
        // Otherwise the "reveal the last four" rule would reveal everything.
        assertThat(SensitiveValues.maskTail("1234")).isEqualTo("XXXX");
        assertThat(SensitiveValues.maskTail("12")).isEqualTo("XX");
    }

    @Test
    @DisplayName("null and blank mask to null")
    void nullsAreNull() {
        assertThat(SensitiveValues.maskAadhaar(null)).isNull();
        assertThat(SensitiveValues.maskPan("")).isNull();
        assertThat(SensitiveValues.maskAccountNumber("  ")).isNull();
    }

    @Test
    @DisplayName("a mask never contains the middle of the original")
    void nothingLeaksThroughTheMask() {
        assertThat(SensitiveValues.maskAadhaar("234512341234")).doesNotContain("2345123");
        assertThat(SensitiveValues.maskPan("AABCS1234A")).doesNotContain("AABCS1");
    }
}
