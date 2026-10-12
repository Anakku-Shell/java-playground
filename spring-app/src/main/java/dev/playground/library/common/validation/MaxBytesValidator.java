package dev.playground.library.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** Checks {@link MaxBytes}. Guide: §5.7 Security. */
public class MaxBytesValidator implements ConstraintValidator<MaxBytes, String> {

    private int max;

    @Override
    public void initialize(MaxBytes annotation) {
        this.max = annotation.value();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
