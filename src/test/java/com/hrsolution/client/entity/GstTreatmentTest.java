package com.hrsolution.client.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GST treatment: whether an invoice carries CGST+SGST or IGST.
 *
 * <p>Small logic with outsized consequences. Getting it wrong does not merely
 * mis-state a total - it files the tax under the wrong heads, which is a
 * correction with the GST department rather than a corrected invoice. Hence the
 * coverage out of proportion to the line count.
 */
class GstTreatmentTest {

    @ParameterizedTest(name = "company {0} + client {1} = {2}")
    @CsvSource({
            // Same state: the rate splits into CGST and SGST.
            "27, 27, INTRA_STATE",
            "07, 07, INTRA_STATE",
            // Different states: charged entirely as IGST.
            "27, 29, INTER_STATE",
            "27, 07, INTER_STATE",
            "29, 27, INTER_STATE"
    })
    void resolvesFromStateCodes(String companyCode, String clientCode, GstTreatment expected) {
        assertThat(GstTreatment.resolve(companyCode, clientCode)).isEqualTo(expected);
    }

    @Test
    @DisplayName("a missing code on either side is UNKNOWN, never a guess")
    void missingCodeIsUnknown() {
        // The alternative - defaulting to one treatment - would silently file
        // tax under the wrong heads for every client onboarded before its
        // paperwork arrived.
        assertThat(GstTreatment.resolve(null, "27")).isEqualTo(GstTreatment.UNKNOWN);
        assertThat(GstTreatment.resolve("27", null)).isEqualTo(GstTreatment.UNKNOWN);
        assertThat(GstTreatment.resolve(null, null)).isEqualTo(GstTreatment.UNKNOWN);
        assertThat(GstTreatment.resolve("", "27")).isEqualTo(GstTreatment.UNKNOWN);
        assertThat(GstTreatment.resolve("27", "   ")).isEqualTo(GstTreatment.UNKNOWN);
    }

    @Test
    @DisplayName("tolerates surrounding whitespace")
    void trimsWhitespace() {
        // A code pasted from a spreadsheet often arrives padded, and that must
        // not flip an intra-state client to inter-state.
        assertThat(GstTreatment.resolve(" 27 ", "27")).isEqualTo(GstTreatment.INTRA_STATE);
        assertThat(GstTreatment.resolve("27", " 27")).isEqualTo(GstTreatment.INTRA_STATE);
    }

    @Test
    @DisplayName("comparison is exact: 7 and 07 are different codes")
    void comparisonIsExact() {
        // GST state codes are two digits. "7" is not a valid code, and treating
        // it as equal to "07" would hide a data-entry error that produces a
        // wrong tax head.
        assertThat(GstTreatment.resolve("7", "07")).isEqualTo(GstTreatment.INTER_STATE);
    }

    @Test
    @DisplayName("a client reports its own treatment against the company code")
    void clientDerivesItsOwnTreatment() {
        Client client = new Client();
        client.setBillingStateCode("27");

        assertThat(client.gstTreatmentAgainst("27")).isEqualTo(GstTreatment.INTRA_STATE);
        assertThat(client.gstTreatmentAgainst("29")).isEqualTo(GstTreatment.INTER_STATE);
        assertThat(client.gstTreatmentAgainst(null)).isEqualTo(GstTreatment.UNKNOWN);

        client.setBillingStateCode(null);
        assertThat(client.gstTreatmentAgainst("27")).isEqualTo(GstTreatment.UNKNOWN);
    }
}
