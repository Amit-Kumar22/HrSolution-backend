package com.hrsolution.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a record cannot be found by the identifier supplied, or exists but
 * is soft-deleted. Maps to HTTP 404.
 */
public class ResourceNotFoundException extends ApiException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    /**
     * Builds a uniform message such as {@code Worker not found with id '42'}.
     *
     * @param resource human-readable entity name, e.g. {@code "Worker"}
     * @param field    the field looked up by, e.g. {@code "id"}
     * @param value    the value that produced no match
     */
    public static ResourceNotFoundException of(String resource, String field, Object value) {
        return new ResourceNotFoundException(
                "%s not found with %s '%s'".formatted(resource, field, value));
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return of(resource, "id", id);
    }
}
