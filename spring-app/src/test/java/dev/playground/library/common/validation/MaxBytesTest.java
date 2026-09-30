package dev.playground.library.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * {@code @MaxBytes}: a length limit in UTF-8 bytes, where {@code @Size} counts characters. Used for
 * passwords, because BCrypt reads at most 72 bytes. Guide: §5.7 Security.
 */
class MaxBytesTest {

    record Holder(@Size(max = 72) @MaxBytes(72) String password) {}

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
    void nullIsLeftToNotNull() {
        assertThat(validator.validate(new Holder(null))).isEmpty();
    }

    @Test
    void seventyTwoAsciiCharactersAreSeventyTwoBytes() {
        assertThat(validator.validate(new Holder("a".repeat(72)))).isEmpty();
        assertThat(validator.validate(new Holder("a".repeat(73))))
                .extracting(ConstraintViolation::getMessage)
                .contains("must be at most 72 bytes in UTF-8");
    }

    @Test
    void charactersOutsideAsciiTakeMoreThanOneByte() {
        // "ñ" is 2 bytes in UTF-8: 40 characters, 80 bytes. @Size lets it through; @MaxBytes does not.
        assertThat(validator.validate(new Holder("ñ".repeat(40))))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("must be at most 72 bytes in UTF-8");
    }
}
