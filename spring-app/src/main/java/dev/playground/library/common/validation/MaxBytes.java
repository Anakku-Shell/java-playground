package dev.playground.library.common.validation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.RECORD_COMPONENT;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * A string of at most {@code value} bytes in UTF-8; null is valid. {@code @Size} counts characters,
 * and outside ASCII one character takes 2 to 4 bytes. BCrypt reads only the first 72 bytes of a
 * password (Spring Security rejects longer ones with an exception), so the limit that matters for a
 * password is in bytes. Guide: §5.7 Security.
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface MaxBytes {

    /** The maximum number of UTF-8 bytes. */
    int value();

    String message() default "must be at most {value} bytes in UTF-8";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
