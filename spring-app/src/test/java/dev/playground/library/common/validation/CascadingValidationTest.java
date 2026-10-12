package dev.playground.library.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Bean Validation checks a nested object only when the field that holds it has {@code @Valid}. The
 * same rule applies to request bodies. {@code @ConfigurationProperties} is the exception: Spring
 * Boot validates every nested object it binds. Guide: §5.3 Validation & errors.
 */
class CascadingValidationTest {

    record Line(@NotBlank String title) {}

    record Order(@Valid Line checked, Line unchecked, List<@Valid Line> lines) {}

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
    void validCascadesOnlyIntoTheAnnotatedFieldsAndElements() {
        var blank = new Line("");

        var violations = validator.validate(new Order(blank, blank, List.of(blank)));

        // "unchecked" holds the same invalid Line, but without @Valid nobody looks inside it.
        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("checked.title", "lines[0].title");
    }
}
