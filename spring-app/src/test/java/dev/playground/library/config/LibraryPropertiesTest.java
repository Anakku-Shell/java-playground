package dev.playground.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

/**
 * Binding {@code library.*} from {@code application.yml} into the {@link LibraryProperties} record.
 * Guide: §5.1 Spring Boot fundamentals.
 *
 * <p>{@code classes = Config.class} builds a context with only this binding instead of the whole
 * application. Spring Boot still loads {@code application.yml}, so the test sees the real file.
 * The {@code properties} attribute adds a source with a higher precedence than the file, the same
 * way a command-line argument or an environment variable would.
 */
@SpringBootTest(classes = LibraryPropertiesTest.Config.class, properties = "library.loans.max-active=5")
class LibraryPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(LibraryProperties.class)
    static class Config {}

    // Field injection is fine in tests: JUnit, not Spring, creates the test instance.
    @Autowired
    private LibraryProperties properties;

    @Test
    void bindsTheNameFromApplicationYml() {
        assertThat(properties.name()).isEqualTo("Playground Library");
    }

    @Test
    void testPropertySetsAKeyTheFileLacks() {
        // application.yml does not set max-active; the test property does. Relaxed binding maps
        // the kebab-case key max-active to the maxActive component.
        assertThat(properties.loans().maxActive()).isEqualTo(5);
    }

    @Test
    void defaultValueFillsAMissingProperty() {
        assertThat(properties.loans().durationDays()).isEqualTo(14);
    }

    /** Adds a property that application.yml also sets: the higher-precedence source wins. */
    @Nested
    @TestPropertySource(properties = "library.name=From a test property")
    class WhenTheFileAndATestPropertyDisagree {

        @Autowired
        private LibraryProperties overridden;

        @Test
        void theTestPropertyWins() {
            assertThat(overridden.name()).isEqualTo("From a test property");
        }
    }

    @Test
    void emptyDefaultValueCreatesAMissingNestedGroup() {
        // No library.loans.* key at all (the runner does not read application.yml). The empty
        // @DefaultValue on `loans` still builds the nested record with its own defaults.
        new ApplicationContextRunner()
                .withUserConfiguration(Config.class)
                .withPropertyValues("library.name=Bare")
                .run(context -> assertThat(
                                context.getBean(LibraryProperties.class).loans())
                        .isEqualTo(new LibraryProperties.Loans(3, 14)));
    }
}
