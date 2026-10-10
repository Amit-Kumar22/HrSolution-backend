package com.hrsolution.client.entity;

/**
 * Which GST components an invoice to a client attracts.
 *
 * <p>Determined solely by comparing the client's billing state code with the
 * company's own. Getting it wrong does not just mis-state a total - it files
 * the tax under the wrong heads, which is a correction with the GST department
 * rather than a corrected invoice.
 */
public enum GstTreatment {

    /** Same state: the rate splits into CGST and SGST, half each. */
    INTRA_STATE,

    /** Different states: the full rate is charged as IGST. */
    INTER_STATE,

    /**
     * Cannot be determined, because one of the two state codes is missing.
     * Phase 9 refuses to issue an invoice in this state rather than guessing.
     */
    UNKNOWN;

    /**
     * Compares two GST state codes.
     *
     * @param companyStateCode the service provider's code, from company settings
     * @param clientStateCode  the client's billing state code
     */
    public static GstTreatment resolve(String companyStateCode, String clientStateCode) {
        if (isBlank(companyStateCode) || isBlank(clientStateCode)) {
            return UNKNOWN;
        }
        return companyStateCode.trim().equals(clientStateCode.trim()) ? INTRA_STATE : INTER_STATE;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
