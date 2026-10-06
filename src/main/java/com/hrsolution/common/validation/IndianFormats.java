package com.hrsolution.common.validation;

/**
 * Regular expressions for the Indian identifiers this platform validates.
 *
 * <p>Every pattern starts with {@code ^$|} so that an <em>empty string</em>
 * passes. Combined with {@code @Pattern}'s standard behaviour of treating
 * {@code null} as valid, this makes each constant usable on an optional field
 * without a caller having to send {@code null} rather than {@code ""} - which
 * is what a cleared form field actually submits. Put {@code @NotBlank} alongside
 * when the field is mandatory.
 *
 * <p>These check <em>shape</em>, not existence or checksum. A structurally valid
 * GSTIN is not necessarily a registered one; verifying that needs the GSTN API.
 */
public final class IndianFormats {

    /**
     * GSTIN: 2-digit state code, 10-character PAN, entity number, {@code Z},
     * then a checksum character. Example {@code 27AABCS1234A1Z5}.
     */
    public static final String GSTIN = "^$|^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";
    public static final String GSTIN_MESSAGE = "must be a valid 15-character GSTIN, e.g. 27AABCS1234A1Z5";

    /** PAN: five letters, four digits, one letter. Example {@code AABCS1234A}. */
    public static final String PAN = "^$|^[A-Z]{5}[0-9]{4}[A-Z]$";
    public static final String PAN_MESSAGE = "must be a valid 10-character PAN, e.g. AABCS1234A";

    /** TAN: four letters, five digits, one letter. Example {@code PNEA12345B}. */
    public static final String TAN = "^$|^[A-Z]{4}[0-9]{5}[A-Z]$";
    public static final String TAN_MESSAGE = "must be a valid 10-character TAN, e.g. PNEA12345B";

    /** CIN: 21 characters, as issued by the MCA. */
    public static final String CIN = "^$|^[LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6}$";
    public static final String CIN_MESSAGE = "must be a valid 21-character CIN";

    /** Two-digit GST state code, {@code 01} to {@code 38}. */
    public static final String GST_STATE_CODE = "^$|^[0-3][0-9]$";
    public static final String GST_STATE_CODE_MESSAGE = "must be a two-digit GST state code, e.g. 27";

    /** Six-digit PIN code, never starting with zero. */
    public static final String PINCODE = "^$|^[1-9][0-9]{5}$";
    public static final String PINCODE_MESSAGE = "must be a valid 6-digit PIN code";

    /** IFSC: four letters, {@code 0}, then six alphanumerics. Example {@code HDFC0001234}. */
    public static final String IFSC = "^$|^[A-Z]{4}0[A-Z0-9]{6}$";
    public static final String IFSC_MESSAGE = "must be a valid 11-character IFSC, e.g. HDFC0001234";

    /** Landline or mobile, with optional country code, spaces, dashes and brackets. */
    public static final String PHONE = "^$|^[0-9+()\\-\\s]{7,20}$";
    public static final String PHONE_MESSAGE = "must be a valid phone number";

    /** Indian mobile: ten digits starting 6-9, no country code. */
    public static final String MOBILE = "^$|^[6-9][0-9]{9}$";
    public static final String MOBILE_MESSAGE = "must be a valid 10-digit Indian mobile number";

    /** Aadhaar: twelve digits, never starting 0 or 1. */
    public static final String AADHAAR = "^$|^[2-9][0-9]{11}$";
    public static final String AADHAAR_MESSAGE = "must be a valid 12-digit Aadhaar number";

    /** UAN (PF universal account number): twelve digits. */
    public static final String UAN = "^$|^[0-9]{12}$";
    public static final String UAN_MESSAGE = "must be a valid 12-digit UAN";

    /** ESIC insured person number: ten digits. */
    public static final String ESIC_IP = "^$|^[0-9]{10}$";
    public static final String ESIC_IP_MESSAGE = "must be a valid 10-digit ESIC IP number";

    /** Bank account number: 9 to 18 digits, covering all Indian banks. */
    public static final String BANK_ACCOUNT = "^$|^[0-9]{9,18}$";
    public static final String BANK_ACCOUNT_MESSAGE = "must be a valid bank account number (9-18 digits)";

    private IndianFormats() {
    }
}
