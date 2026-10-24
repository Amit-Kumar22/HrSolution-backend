package com.hrsolution.common.crypto;

/**
 * Masking for the values that are encrypted at rest.
 *
 * <p>A plain static class rather than methods on the entities, because the same
 * masking has to apply to a worker's Aadhaar, a bank account number on a
 * different table, and anything Phase 8 adds. Keeping it in one place means
 * "what does a masked Aadhaar look like" has one answer.
 *
 * <p>Nothing here is a security boundary on its own - the boundary is the
 * {@code WORKER_SENSITIVE_READ} check in the service that decides whether to
 * call {@code mask} or return the real value. These functions only make sure
 * that when the answer is "mask it", the result is consistent and leaks no more
 * than the last four characters.
 */
public final class SensitiveValues {

    private SensitiveValues() {
    }

    /** How many trailing characters a mask reveals. */
    private static final int VISIBLE_SUFFIX = 4;

    /**
     * {@code 123412341234} to {@code XXXX-XXXX-1234}.
     *
     * <p>The grouped form is what Indian forms and payslips show, so staff
     * recognise it as a masked Aadhaar rather than a corrupted value.
     */
    public static String maskAadhaar(String aadhaar) {
        if (aadhaar == null || aadhaar.isBlank()) {
            return null;
        }
        String digits = aadhaar.replaceAll("\\s", "");
        if (digits.length() <= VISIBLE_SUFFIX) {
            return "X".repeat(digits.length());
        }
        return "XXXX-XXXX-" + digits.substring(digits.length() - VISIBLE_SUFFIX);
    }

    /** {@code AABCS1234A} to {@code XXXXXX234A}. */
    public static String maskPan(String pan) {
        return maskTail(pan);
    }

    /** {@code 123456789012} to {@code XXXXXXXX9012}. */
    public static String maskAccountNumber(String accountNumber) {
        return maskTail(accountNumber);
    }

    /** Masks everything but the last four characters, preserving length. */
    public static String maskTail(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= VISIBLE_SUFFIX) {
            return "X".repeat(trimmed.length());
        }
        return "X".repeat(trimmed.length() - VISIBLE_SUFFIX)
                + trimmed.substring(trimmed.length() - VISIBLE_SUFFIX);
    }
}
