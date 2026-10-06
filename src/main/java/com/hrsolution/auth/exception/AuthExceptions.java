package com.hrsolution.auth.exception;

import com.hrsolution.common.error.ApiException;
import com.hrsolution.common.error.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Failures specific to authentication. */
public final class AuthExceptions {

    private AuthExceptions() {
    }

    /**
     * Wrong email or wrong password. HTTP 401.
     *
     * <p>The message is always identical regardless of which it was, so the
     * endpoint cannot be used to discover whether an email is registered.
     */
    public static class AuthenticationFailed extends ApiException {

        private static final long serialVersionUID = 1L;

        public static final String GENERIC_MESSAGE = "Invalid email or password.";

        public AuthenticationFailed() {
            super(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, GENERIC_MESSAGE);
        }
    }

    /**
     * Correct password, but the account is temporarily locked after repeated
     * failures. HTTP 401 with the remaining wait.
     *
     * <p>This does tell the caller that the account exists, which the generic
     * message above is careful not to. The trade is deliberate: without it a
     * user who has simply mistyped five times sees "invalid password" while
     * typing the correct one, with no way to understand why. Enumeration is
     * instead contained by the per-IP and per-email rate limits, which bite
     * long before an attacker could sweep a list of addresses.
     */
    @Getter
    public static class AccountLocked extends ApiException {

        private static final long serialVersionUID = 1L;

        private final long retryAfterSeconds;

        public AccountLocked(long retryAfterSeconds) {
            super(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED,
                    "Too many failed sign-in attempts. This account is locked for another %d minute(s)."
                            .formatted(Math.max(1, retryAfterSeconds / 60)));
            this.retryAfterSeconds = retryAfterSeconds;
        }
    }

    /**
     * The password was right, but the account may not be used yet - email
     * unverified, awaiting approval, or disabled. HTTP 403.
     *
     * <p>Raised only <em>after</em> the password has been verified. Checking
     * status first would reveal an account's state to anyone who guessed the
     * email, so the order in {@code AuthService.login} matters.
     */
    public static class AccountNotActive extends ApiException {

        private static final long serialVersionUID = 1L;

        public AccountNotActive(String message) {
            super(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, message);
        }
    }

    /** A verification or reset token is unknown, expired or already used. HTTP 400. */
    public static class InvalidToken extends ApiException {

        private static final long serialVersionUID = 1L;

        public InvalidToken(String message) {
            super(ErrorCode.VALIDATION_FAILED, HttpStatus.BAD_REQUEST, message);
        }
    }
}
