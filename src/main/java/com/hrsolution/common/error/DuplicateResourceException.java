package com.hrsolution.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when creating or updating a record would break a uniqueness rule -
 * a duplicate email, GSTIN, employee code or invoice number. Maps to HTTP 409.
 *
 * <p>Prefer throwing this after an explicit existence check so the client gets a
 * clear message, rather than letting the database constraint surface as a
 * {@code DataIntegrityViolationException}.
 */
public class DuplicateResourceException extends ApiException {

    private static final long serialVersionUID = 1L;

    public DuplicateResourceException(String message) {
        super(ErrorCode.DUPLICATE_RESOURCE, HttpStatus.CONFLICT, message);
    }

    public static DuplicateResourceException of(String resource, String field, Object value) {
        return new DuplicateResourceException(
                "%s already exists with %s '%s'".formatted(resource, field, value));
    }
}
