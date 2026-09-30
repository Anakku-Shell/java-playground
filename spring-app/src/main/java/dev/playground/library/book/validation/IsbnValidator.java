package dev.playground.library.book.validation;

import dev.playground.library.book.Isbn;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/** Checks {@link ValidIsbn}. Guide: §5.3 Validation & errors. */
public class IsbnValidator implements ConstraintValidator<ValidIsbn, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || value.isBlank() || Isbn.isValid(value);
    }
}
