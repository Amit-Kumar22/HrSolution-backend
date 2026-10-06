package com.hrsolution.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for exceptions that carry their own HTTP status and {@link ErrorCode}.
 *
 * <p>{@code GlobalExceptionHandler} translates any subclass into a ProblemDetail
 * response automatically, so throwing one of these from a service is all that is
 * needed - controllers never catch them.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;
    private final HttpStatus status;

    protected ApiException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected ApiException(ErrorCode errorCode, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }
}
