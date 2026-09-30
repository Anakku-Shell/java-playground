package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

/**
 * The HTTP contract of {@link AuthorController}: paths, status codes, headers and JSON. The service
 * is a Mockito mock, so this test checks only the web layer. Guide: §5.2 REST API.
 *
 * <p>{@code @MockitoBean} (Spring Framework 6.2+) replaces the old {@code @MockBean}: it puts a mock
 * into the context in place of the real bean.
 */
@WebMvcTest(AuthorController.class)
class AuthorControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private AuthorService service;

    @Test
    void listReturnsAllAuthors() {
        given(service.findAll())
                .willReturn(List.of(
                        new AuthorResponse(1L, "Jane Austen", 1775), new AuthorResponse(2L, "Frank Herbert", 1920)));

        assertThat(mvc.get().uri("/api/authors")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        [
                          {"id": 1, "name": "Jane Austen", "birthYear": 1775},
                          {"id": 2, "name": "Frank Herbert", "birthYear": 1920}
                        ]
                        """);
    }

    @Test
    void getReturnsOneAuthor() {
        given(service.findById(1L)).willReturn(new AuthorResponse(1L, "Jane Austen", null));

        // A null component is written as "birthYear": null (Jackson's default).
        assertThat(mvc.get().uri("/api/authors/{id}", 1))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        {"id": 1, "name": "Jane Austen", "birthYear": null}
                        """);
    }

    @Test
    void getMissingIs404() {
        given(service.findById(99L))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Author 99 not found"));

        assertThat(mvc.get().uri("/api/authors/99")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void createReturns201WithLocation() {
        given(service.create(new CreateAuthorRequest("Carl Sagan", 1934)))
                .willReturn(new AuthorResponse(7L, "Carl Sagan", 1934));

        assertThat(mvc.post()
                        .uri("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Carl Sagan", "birthYear": 1934}
                                """))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/authors/7")
                .bodyJson()
                .extractingPath("$.id")
                .isEqualTo(7);
    }

    @Test
    void updateReturns200() {
        given(service.update(eq(7L), any(UpdateAuthorRequest.class)))
                .willReturn(new AuthorResponse(7L, "Carl Sagan", 1934));

        assertThat(mvc.put()
                        .uri("/api/authors/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Carl Sagan", "birthYear": 1934}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.name")
                .isEqualTo("Carl Sagan");
        verify(service).update(7L, new UpdateAuthorRequest("Carl Sagan", 1934));
    }

    @Test
    void deleteReturns204() {
        assertThat(mvc.delete().uri("/api/authors/7"))
                .hasStatus(HttpStatus.NO_CONTENT)
                .body()
                .isEmpty();
        verify(service).delete(7L);
    }

    @Test
    void deleteMissingIs404() {
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .given(service)
                .delete(99L);

        assertThat(mvc.delete().uri("/api/authors/99")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void unknownFieldsAreIgnoredAndMissingOnesAreNull() {
        given(service.create(new CreateAuthorRequest("Mary Beard", null)))
                .willReturn(new AuthorResponse(8L, "Mary Beard", null));

        // Jackson 3 does not fail on unknown properties by default (with Jackson 2, Spring Boot
        // switched that off for you), so "nickname" is dropped. The missing Integer binds as null.
        assertThat(mvc.post()
                        .uri("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Mary Beard", "nickname": "SPQR"}
                                """))
                .hasStatus(HttpStatus.CREATED);
        verify(service).create(new CreateAuthorRequest("Mary Beard", null));
    }
}
