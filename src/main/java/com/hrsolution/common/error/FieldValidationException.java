package com.hrsolution.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * A validation failure raised from a service, for rules Bean Validation cannot
 * express on a single field.
 *
 * <p>Produces exactly the same response shape as an annotation-driven failure -
 * {@code errorCode: VALIDATION_FAILED} with an {@code errors} array - so a
 * client has one error format to handle regardless of where the rule lives.
 *
 * <p>Used for cross-field and stateful rules: "password must not be your email
 * address", "new password must differ from the current one", "end date must be
 * after start date".
 */
@Getter
public class FieldValidationException extends ApiException {

    private static final long serialVersionUID = 1L;

    private final transient List<ApiFieldError> fieldErrors;

    public FieldValidationException(List<ApiFieldError> fieldErrors) {
        super(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST,
                "Request contains %d invalid field(s).".formatted(fieldErrors.size()));
        this.fieldErrors = List.copyOf(fieldErrors);
    }

    /** Convenience for the common single-field case. */
    public static FieldValidationException of(String field, String message) {
        return new FieldValidationException(List.of(ApiFieldError.of(field, message, null)));
    }
}
