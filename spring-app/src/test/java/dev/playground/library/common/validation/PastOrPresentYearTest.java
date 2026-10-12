package dev.playground.library.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.Year;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** {@code @PastOrPresentYear}: a year between 0 and the current one. Guide: §5.3. */
class PastOrPresentYearTest {

    record Holder(@PastOrPresentYear Integer year) {}

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
    void nullIsValidBecauseTheYearIsOptional() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
    }

    @Test
    void zeroAndTheCurrentYearAreTheBounds() {
        assertThat(validator.validate(new Holder(0))).isEmpty();
        assertThat(validator.validate(new Holder(Year.now().getValue()))).isEmpty();
    }

    @Test
    void nextYearIsAViolation() {
        // @Max needs a compile-time constant, so "at most this year" needs a custom constraint.
        assertThat(validator.validate(new Holder(Year.now().getValue() + 1)))
                .singleElement()
                .satisfies(v -> assertThat(v.getMessage()).isEqualTo("must be a year between 0 and the current year"));
    }

    @Test
    void negativeYearIsAViolation() {
        assertThat(validator.validate(new Holder(-1))).hasSize(1);
    }
}
