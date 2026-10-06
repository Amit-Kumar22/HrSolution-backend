package com.hrsolution.common.error;

/**
 * Stable, machine-readable error identifiers returned in the {@code errorCode}
 * field of every error response.
 *
 * <p>API clients should branch on these rather than on HTTP status or on the
 * human-readable {@code detail} message, both of which are allowed to change.
 * Never rename or reuse a constant - add a new one instead.
 */
public enum ErrorCode {

    /** Request body or parameters failed Bean Validation. */
    VALIDATION_FAILED,
    /** Syntactically broken request - unparseable JSON, wrong parameter type. */
    MALFORMED_REQUEST,
    /** The requested entity does not exist, or is soft-deleted. */
    RESOURCE_NOT_FOUND,
    /** A uniqueness rule would be broken, e.g. an email already registered. */
    DUPLICATE_RESOURCE,
    /** A domain rule forbids the operation in the record's current state. */
    BUSINESS_RULE_VIOLATION,
    /** Someone else modified the record first (optimistic lock, {@code @Version}). */
    CONCURRENT_MODIFICATION,
    /** A database constraint rejected the write. */
    DATA_INTEGRITY_VIOLATION,
    /** No credentials, or invalid/expired credentials. */
    UNAUTHENTICATED,
    /** Authenticated, but lacking the required permission or not the owner. */
    ACCESS_DENIED,
    /** Too many requests - see the {@code Retry-After} header. */
    RATE_LIMIT_EXCEEDED,
    /** Wrong HTTP method for this path. */
    METHOD_NOT_ALLOWED,
    /** Unsupported {@code Content-Type}. */
    UNSUPPORTED_MEDIA_TYPE,
    /** Upload exceeded the configured maximum size. */
    PAYLOAD_TOO_LARGE,
    /** Unhandled server-side failure. Details are logged, never returned. */
    INTERNAL_ERROR
}
