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
 * A year between 0 and the current year; null is valid. {@code @Max} takes only a compile-time
 * constant, so "no later than this year" needs a validator that reads the clock. (A
 * {@code java.time.Year} field could use the standard {@code @PastOrPresent}; the domain model
 * uses {@code Integer}.) Guide: §5.3.
 */
@Documented
@Constraint(validatedBy = PastOrPresentYearValidator.class)
@Target({FIELD, PARAMETER, RECORD_COMPONENT, TYPE_USE})
@Retention(RUNTIME)
public @interface PastOrPresentYear {

    String message() default "must be a year between 0 and the current year";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
