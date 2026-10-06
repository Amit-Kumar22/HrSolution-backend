package com.hrsolution.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Checks the {@link StrongPassword} rules.
 *
 * <p>Implemented by scanning the characters rather than with a regular
 * expression. A single regex covering four lookaheads is write-only code, and
 * nested quantifiers in that shape are a known source of catastrophic
 * backtracking - on a field an unauthenticated caller controls, that is a
 * denial-of-service vector. This loop is linear and obvious.
 */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    static final int MIN_LENGTH = 8;

    /**
     * Upper bound to stop a multi-megabyte "password" reaching BCrypt, which
     * would burn CPU hashing it. Well above any real password.
     *
     * <p>Note BCrypt itself only considers the first 72 bytes, so an extremely
     * long password offers no additional strength regardless.
     */
    static final int MAX_LENGTH = 128;

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // Null is left to @NotBlank, so that a missing password reports once
        // rather than producing two errors on the same field.
        if (password == null) {
            return true;
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return false;
        }

        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        boolean hasSpecial = false;

        for (char character : password.toCharArray()) {
            if (Character.isUpperCase(character)) {
                hasUpper = true;
            } else if (Character.isLowerCase(character)) {
                hasLower = true;
            } else if (Character.isDigit(character)) {
                hasDigit = true;
            } else {
                // Anything that is not a letter or digit counts, including
                // spaces and non-ASCII punctuation - there is no reason to
                // reject a passphrase for using a character not on a US layout.
                hasSpecial = true;
            }
        }

        return hasUpper && hasLower && hasDigit && hasSpecial;
    }
}
