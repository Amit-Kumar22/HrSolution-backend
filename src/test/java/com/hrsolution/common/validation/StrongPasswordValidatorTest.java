package com.hrsolution.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class StrongPasswordValidatorTest {

    private final StrongPasswordValidator validator = new StrongPasswordValidator();

    private boolean valid(String password) {
        return validator.isValid(password, null);
    }

    @ParameterizedTest(name = "accepts \"{0}\"")
    @ValueSource(strings = {
            "Demo@12345",
            "Str0ng@Pass",
            "Aa1!aaaa",                       // exactly the 8-character minimum
            "Correct Horse Battery 9!",       // a passphrase; the space is the special
            "Pässw0rd!",                      // non-ASCII letters are fine
            "Xx9£aaaa"                   // a non-US-layout symbol counts as special
    })
    void acceptsCompliantPasswords(String password) {
        assertThat(valid(password)).isTrue();
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {
            "Aa1!aaa",       // 7 characters, one short
            "demo@12345",    // no upper case
            "DEMO@12345",    // no lower case
            "Demo@abcde",    // no digit
            "Demo123456",    // no special character
            "",              // empty
            "        "       // only spaces: no letters, no digit
    })
    void rejectsNonCompliantPasswords(String password) {
        assertThat(valid(password)).isFalse();
    }

    @Test
    @DisplayName("null passes, leaving the missing-value error to @NotBlank")
    void nullIsDeferredToNotBlank() {
        // Returning false here would report two errors on one field for a
        // single mistake.
        assertThat(valid(null)).isTrue();
    }

    @Test
    @DisplayName("rejects an absurdly long password rather than hashing it")
    void rejectsOverlyLongPassword() {
        // Guards the BCrypt call from a multi-megabyte input an unauthenticated
        // caller controls. BCrypt only reads the first 72 bytes anyway, so the
        // extra length buys no strength.
        String tooLong = "Aa1!" + "x".repeat(StrongPasswordValidator.MAX_LENGTH);
        assertThat(valid(tooLong)).isFalse();
    }

    @Test
    @DisplayName("accepts a password exactly at the maximum length")
    void acceptsAtMaxLength() {
        String atLimit = "Aa1!" + "x".repeat(StrongPasswordValidator.MAX_LENGTH - 4);
        assertThat(atLimit).hasSize(StrongPasswordValidator.MAX_LENGTH);
        assertThat(valid(atLimit)).isTrue();
    }
}
