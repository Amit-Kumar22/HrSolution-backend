package com.hrsolution.recruitment;

/**
 * The quotable reference for an application, e.g. {@code APP-000123}.
 *
 * <p>A separate class rather than a method on the mapper, for the reason
 * documented in {@code docs/decisions.md} §24: any non-private method on a
 * MapStruct interface becomes a candidate type conversion for its signature,
 * and a {@code String f(Long)} helper would be silently applied to every
 * {@code Long} to {@code String} mapping on that mapper. Keeping it static and
 * off the interface removes the possibility.
 *
 * <p>Mirrors {@code EnquiryReference} from Phase 3 and {@code Client.clientCode}
 * from Phase 4: derived from the id rather than stored, because a second source
 * of truth for an identifier is a second thing that can disagree.
 */
public final class ApplicationReference {

    private ApplicationReference() {
    }

    public static String of(Long applicationId) {
        return applicationId == null ? null : "APP-%06d".formatted(applicationId);
    }
}
