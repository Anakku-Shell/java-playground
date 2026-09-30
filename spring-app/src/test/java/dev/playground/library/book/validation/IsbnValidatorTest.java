package dev.playground.library.book.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code @ValidIsbn} through a real Bean Validation {@link Validator}, without Spring: the same
 * engine (Hibernate Validator) that checks {@code @Valid} request bodies. Guide: §5.3.
 */
class IsbnValidatorTest {

    record Holder(@ValidIsbn String isbn) {}

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    @Test
    void validIsbn13HasNoViolation() {
        assertThat(validator.validate(new Holder("9780441013593"))).isEmpty();
    }

    @Test
    void validIsbn10HasNoViolation() {
        assertThat(validator.validate(new Holder("0441013597"))).isEmpty();
    }

    @Test
    void badChecksumIsAViolationOnTheField() {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder("9780441013594"));

        assertThat(violations).singleElement().satisfies(v -> {
            assertThat(v.getPropertyPath()).hasToString("isbn");
            assertThat(v.getMessage()).isEqualTo("must be a valid ISBN-10 or ISBN-13");
        });
    }

    @Test
    void nullAndBlankAreLeftToNotBlank() {
        // Bean Validation convention: every constraint except @NotNull/@NotBlank accepts null, so
        // constraints compose (@NotBlank @ValidIsbn) instead of each one reporting the same problem.
        assertThat(validator.validate(new Holder(null))).isEmpty();
        assertThat(validator.validate(new Holder(" "))).isEmpty();
    }
}
