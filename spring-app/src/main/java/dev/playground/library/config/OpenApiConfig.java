package dev.playground.library.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Metadata for the generated OpenAPI document. springdoc builds the paths and schemas from the
 * controllers and DTO records by itself; this bean only adds the title and version shown at the
 * top of Swagger UI ({@code /swagger-ui.html}). Guide: §5.2 REST API.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    OpenAPI libraryOpenApi(LibraryProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title(properties.name())
                        .version("v1")
                        .description("A learning playground: authors, books and (later) loans."));
    }
}
