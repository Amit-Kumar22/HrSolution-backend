package com.hrsolution.enquiry;

/**
 * Builds the human-quotable enquiry reference, e.g. {@code ENQ-000042}.
 *
 * <p>Derived from the id rather than stored: it carries no information the id
 * does not, and a column would be one more thing to keep in step. Note this is
 * NOT the pattern for invoice numbers - those are a legal series that must never
 * be reused, and get their own table in Phase 9.
 *
 * <p><strong>A static class, not a default method on the mapper.</strong> A
 * non-private method on a MapStruct interface is treated as a candidate
 * conversion for its signature, so a {@code Long -> String} helper gets silently
 * applied to every unrelated {@code Long -> String} mapping in that mapper. See
 * docs/decisions.md for the incident this caused.
 */
public final class EnquiryReference {

    private EnquiryReference() {
    }

    public static String of(Long enquiryId) {
        return enquiryId == null ? null : "ENQ-%06d".formatted(enquiryId);
    }
}
