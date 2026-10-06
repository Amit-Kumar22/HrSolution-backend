package com.hrsolution.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enforces the project password policy: at least 8 characters with an
 * upper-case letter, a lower-case letter, a digit and a special character.
 *
 * <p>Implemented as a Bean Validation constraint rather than a check inside the
 * service so that a weak password is reported as a <em>field</em> error on
 * {@code password}, alongside any other invalid fields, instead of aborting the
 * request at the first problem.
 *
 * <p>The related rule "password must not be your email address" needs two
 * fields and so cannot live here; {@code AuthService} applies it and raises a
 * {@code FieldValidationException}, which produces an identically shaped
 * response.
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {

    String message() default
            "must be at least 8 characters and include an upper-case letter, "
                    + "a lower-case letter, a digit and a special character";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
