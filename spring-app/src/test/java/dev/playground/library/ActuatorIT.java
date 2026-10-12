package dev.playground.library;

import static dev.playground.library.testing.TestDataFactory.ada;
import static dev.playground.library.testing.TestDataFactory.withId;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * What Actuator exposes, and to whom: health and info are public, with health details for
 * librarians; metrics are for librarians; every other endpoint is not exposed over HTTP at all.
 * Same setup as the other MockMvc ITs, so it reuses their context (§5.8). Guide: §5.9 Beyond CRUD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActuatorIT {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void healthIsPublicAndShowsOnlyTheStatus() {
        // What a load balancer or Kubernetes probe needs: UP or DOWN, nothing about the internals.
        // ("groups" lists the probe groups, liveness and readiness: names only.)
        assertThat(mvc.get().uri("/actuator/health"))
                .hasStatusOk()
                .bodyJson()
                .doesNotHavePath("$.components")
                .extractingPath("$.status")
                .isEqualTo("UP");
    }

    @Test
    void aLibrarianSeesWhatTheHealthIsMadeOf() {
        assertThat(mvc.get().uri("/actuator/health").with(TestUsers.librarian()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.components.db.status")
                .isEqualTo("UP");
    }

    @Test
    void infoIsPublicAndNamesTheBuild() {
        // From META-INF/build-info.properties, written by the build-info goal of spring-boot-maven-plugin.
        assertThat(mvc.get().uri("/actuator/info"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.build.artifact")
                .isEqualTo("spring-app");
    }

    @Test
    void metricsAreForLibrarians() {
        assertThat(mvc.get().uri("/actuator/metrics")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/metrics").with(TestUsers.member(withId(ada(), 1L))))
                .hasStatus(HttpStatus.FORBIDDEN);
        assertThat(mvc.get().uri("/actuator/metrics/jvm.memory.used").with(TestUsers.librarian()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("jvm.memory.used");
    }

    @Test
    void endpointsThatAreNotExposedDoNotExist() {
        // env would show configuration values, beans the whole context: not over HTTP, even for a
        // librarian.
        assertThat(mvc.get().uri("/actuator/env").with(TestUsers.librarian())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.get().uri("/actuator/beans").with(TestUsers.librarian())).hasStatus(HttpStatus.NOT_FOUND);
    }
}
