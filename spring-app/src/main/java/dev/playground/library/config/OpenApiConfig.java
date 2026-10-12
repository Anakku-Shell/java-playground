package dev.playground.library.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the generated OpenAPI document. springdoc builds the paths and schemas from the
 * controllers and DTO records by itself; this bean only adds the title and version shown at the
 * top of Swagger UI ({@code /swagger-ui.html}), and the bearer token scheme. Guide: §5.2 REST API,
 * §5.7 Security.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI libraryOpenApi(LibraryProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.name())
                        .version("v1")
                        .description("A learning playground: authors, books, members and loans."))
                // §5.7: a bearer-token scheme, required by every operation. Swagger UI then shows an
                // "Authorize" button: paste the accessToken from POST /api/auth/login once, and it is
                // sent with each request. (Public endpoints ignore it.)
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}
