package dev.playground.library.book.validation;

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
 * A custom Bean Validation constraint: the annotation declares the message and points to the class
 * that does the check. {@code message}, {@code groups} and {@code payload} are required by the spec.
 * Null and blank values are valid; pair it with {@code @NotBlank}. Guide: §5.3 Validation & errors.
 */
@Documented
@Constraint(validatedBy = IsbnValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface ValidIsbn {

    String message() default "must be a valid ISBN-10 or ISBN-13";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
