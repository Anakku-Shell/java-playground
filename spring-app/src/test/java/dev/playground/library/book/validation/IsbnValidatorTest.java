package dev.playground.library.book.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * {@code @ValidIsbn} through a real Bean Validation {@link Validator}, without Spring: the same
 * engine (Hibernate Validator) that checks {@code @Valid} request bodies. Guide: §5.3, §5.8 Testing.
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

    // @MethodSource: the arguments come from a static method, so they can be any objects, built
    // with code. argumentSet gives each case a name for the report instead of its raw values.
    static Stream<Arguments> invalidIsbns() {
        return Stream.of(
                argumentSet("ISBN-13 with a wrong check digit", "9780441013594"),
                argumentSet("ISBN-10 with a wrong check digit", "0441013598"),
                argumentSet("valid checksum, but no 978/979 prefix", "0000000000000"),
                argumentSet("eleven digits", "97804410135"),
                argumentSet("only separators", "--"));
    }

    @ParameterizedTest
    @MethodSource("invalidIsbns")
    void anInvalidIsbnIsAViolationOnTheField(String isbn) {
        Set<ConstraintViolation<Holder>> violations = validator.validate(new Holder(isbn));

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
