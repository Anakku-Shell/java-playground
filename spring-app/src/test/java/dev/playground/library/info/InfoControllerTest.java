package dev.playground.library.info;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.config.LibraryProperties;
import dev.playground.library.security.JwtConfig;
import dev.playground.library.security.SecurityConfig;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * A web slice test: {@code @WebMvcTest} starts only the MVC layer (this controller, Jackson,
 * converters, exception handling) and no Tomcat. Requests go through {@link MockMvcTester}, the
 * AssertJ flavour of MockMvc. Guide: §5.1 Spring Boot fundamentals.
 *
 * <p>The slice skips ordinary {@code @Component}s, so the two greeters are imported explicitly. It
 * also skips what {@code @ConfigurationPropertiesScan} would register, so {@link LibraryProperties}
 * is enabled by hand. {@code application.yml} is read as usual. Anything the controller needs and
 * the slice does not provide fails the context with "No qualifying bean".
 */
@WebMvcTest(InfoController.class)
// The security rules too (§5.7): /api/info is public, so these requests carry no user at all.
@Import({FormalGreeter.class, CasualGreeter.class, SecurityConfig.class, JwtConfig.class})
@EnableConfigurationProperties(LibraryProperties.class)
class InfoControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void infoReturnsTheConfiguration() {
        assertThat(mvc.get().uri("/api/info")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {
                          "application": "library",
                          "name": "Playground Library",
                          "maxActiveLoans": 3,
                          "loanDurationDays": 14,
                          "activeProfiles": []
                        }
                        """);
    }

    @Test
    void greetingsShowPrimaryAndQualifiedBeans() {
        assertThat(mvc.get().uri("/api/info/greetings").param("name", "Ada"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"primary": "Good day, Ada.", "casual": "Hi Ada!"}
                        """);
    }

    @Test
    void greetingsDefaultTheName() {
        assertThat(mvc.get().uri("/api/info/greetings"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.casual")
                .isEqualTo("Hi reader!");
    }

    /** Same controller with the dev profile: application-dev.yml is layered over application.yml. */
    @Nested
    @ActiveProfiles("dev")
    class WithTheDevProfile {

        @Test
        void profileFileOverridesTheNameAndIsListed() {
            assertThat(mvc.get().uri("/api/info")).hasStatusOk().bodyJson().isLenientlyEqualTo("""
                            {"name": "Playground Library (dev)", "activeProfiles": ["dev"]}
                            """);
        }
    }
}
