package dev.playground.library.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.config.LibraryProperties;
import dev.playground.library.info.CasualGreeter;
import dev.playground.library.info.FormalGreeter;
import dev.playground.library.info.InfoController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * CORS as a browser sees it. MockMvc sends the headers a browser would; the browser itself is the
 * one that enforces the answer (a script on another origin cannot read a response without
 * {@code Access-Control-Allow-Origin}). Guide: §5.7 Security.
 */
@WebMvcTest(InfoController.class)
@Import({FormalGreeter.class, CasualGreeter.class, SecurityConfig.class, JwtConfig.class})
@EnableConfigurationProperties(LibraryProperties.class)
class CorsTest {

    private static final String ANGULAR = "http://localhost:4200";

    @Autowired
    private MockMvcTester mvc;

    @Test
    void thePreflightFromTheAngularDevServerIsAllowed() {
        // Before a POST with a JSON body and a bearer token, the browser asks first: OPTIONS, with
        // the method and headers it wants to send. No token: a preflight never carries one.
        assertThat(mvc.options()
                        .uri("/api/books")
                        .header(HttpHeaders.ORIGIN, ANGULAR)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type"))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ANGULAR)
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE")
                .hasHeader(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600");
    }

    @Test
    void aPreflightFromAnotherOriginIsRejected() {
        assertThat(mvc.options()
                        .uri("/api/books")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "DELETE"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .doesNotContainHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);
    }

    @Test
    void anActualRequestNamesTheOriginItAllows() {
        assertThat(mvc.get().uri("/api/info").header(HttpHeaders.ORIGIN, ANGULAR))
                .hasStatusOk()
                .hasHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ANGULAR)
                .hasHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Location");
    }
}
