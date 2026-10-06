package com.hrsolution.common.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a caller exceeds a configured rate limit. Maps to HTTP 429 with a
 * {@code Retry-After} header.
 */
@Getter
public class RateLimitExceededException extends ApiException {

    private static final long serialVersionUID = 1L;

    /** Seconds until the caller may retry; sent as {@code Retry-After}. */
    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(ErrorCode.RATE_LIMIT_EXCEEDED, HttpStatus.TOO_MANY_REQUESTS, message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public static RateLimitExceededException of(long retryAfterSeconds) {
        return new RateLimitExceededException(
                "Too many requests. Try again in %d second(s).".formatted(retryAfterSeconds),
                retryAfterSeconds);
    }
}
