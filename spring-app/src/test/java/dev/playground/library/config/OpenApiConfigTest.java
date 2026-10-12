package dev.playground.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** The OpenAPI document's title comes from {@code library.name}. Guide: §5.2 REST API. */
class OpenApiConfigTest {

    @Test
    void titleComesFromTheLibraryProperties() {
        new ApplicationContextRunner()
                .withUserConfiguration(OpenApiConfig.class, Properties.class)
                .withPropertyValues("library.name=Test Library")
                .run(context -> {
                    OpenAPI api = context.getBean(OpenAPI.class);
                    assertThat(api.getInfo().getTitle()).isEqualTo("Test Library");
                    assertThat(api.getInfo().getVersion()).isEqualTo("v1");
                    // §5.7: Swagger UI shows an "Authorize" button for the bearer token, and sends it
                    // with every request once set.
                    assertThat(api.getComponents()
                                    .getSecuritySchemes()
                                    .get("bearer")
                                    .getScheme())
                            .isEqualTo("bearer");
                    assertThat(api.getSecurity())
                            .extracting(r -> r.containsKey("bearer"))
                            .containsExactly(true);
                });
    }

    @EnableConfigurationProperties(LibraryProperties.class)
    static class Properties {}
}
