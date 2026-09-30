package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Authors from HTTP to PostgreSQL. Same setup as {@code BookControllerIT}, so Spring reuses its
 * cached context (and container). Guide: §5.4 Persistence with JPA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthorControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AuthorRepository repository;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAll();
    }

    @Test
    void createReadUpdateDeleteRoundTrip() throws Exception {
        MvcTestResult created = mvc.post()
                .uri("/api/authors")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Carl Sagn"}
                        """)
                .exchange();
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long id = ((Number) JsonPath.read(created.getResponse().getContentAsString(), "$.id")).longValue();

        assertThat(mvc.put()
                        .uri("/api/authors/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Carl Sagan", "birthYear": 1934}
                                """))
                .hasStatusOk();

        assertThat(mvc.get().uri("/api/authors")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        [{"id": %d, "name": "Carl Sagan", "birthYear": 1934}]
                        """.formatted(id));

        assertThat(mvc.delete().uri("/api/authors/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(repository.existsById(id)).isFalse();
    }
}
