package com.hrsolution.common.error;

/**
 * One field-level validation failure, returned inside the {@code errors} array
 * of a validation ProblemDetail response.
 *
 * @param field         the offending field, in dotted path form for nested
 *                      objects, e.g. {@code contactPerson.email}
 * @param message       the human-readable reason, e.g. {@code must not be blank}
 * @param rejectedValue the value that was rejected. Null for password fields and
 *                      other sensitive inputs, which are never echoed back.
 */
public record ApiFieldError(String field, String message, Object rejectedValue) {

    /** Field names whose values must never be reflected back to the caller. */
    private static final String[] SENSITIVE_FIELDS = {
            "password", "currentpassword", "newpassword", "confirmpassword",
            "token", "secret", "aadhaar", "aadhaarnumber", "pan", "pannumber",
            "bankaccountnumber", "accountnumber", "otp"
    };

    public static ApiFieldError of(String field, String message, Object rejectedValue) {
        return new ApiFieldError(field, message, isSensitive(field) ? null : rejectedValue);
    }

    private static boolean isSensitive(String field) {
        if (field == null) {
            return false;
        }
        String normalised = field.toLowerCase().replace("_", "");
        for (String sensitive : SENSITIVE_FIELDS) {
            if (normalised.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }
}
