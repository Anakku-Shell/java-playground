package dev.playground.library.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.Year;

/** Checks {@link PastOrPresentYear}. Guide: §5.3 Validation & errors. */
public class PastOrPresentYearValidator implements ConstraintValidator<PastOrPresentYear, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        return value == null || (value >= 0 && value <= Year.now().getValue());
    }
}
