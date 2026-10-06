package com.hrsolution.common.error;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a request is well-formed but a domain rule forbids it in the
 * record's current state. Maps to HTTP 422 (Unprocessable Content).
 *
 * <p>Examples from this platform: editing a {@code LOCKED} attendance sheet,
 * deploying a worker on dates that overlap an existing deployment, approving a
 * payroll run that is still in {@code DRAFT}, or editing an {@code ISSUED}
 * invoice instead of raising a credit note.
 */
public class BusinessRuleException extends ApiException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super(ErrorCode.BUSINESS_RULE_VIOLATION, HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
