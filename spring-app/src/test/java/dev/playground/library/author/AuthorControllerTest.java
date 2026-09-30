package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.playground.library.author.dto.AuthorBookResponse;
import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.common.PageResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

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
    void listReturnsAPageSortedByName() {
        given(service.findAll(PageRequest.of(0, 20, Sort.by("name"))))
                .willReturn(new PageResponse<>(
                        List.of(
                                new AuthorResponse(2L, "Frank Herbert", 1920),
                                new AuthorResponse(1L, "Jane Austen", 1775)),
                        0,
                        20,
                        2,
                        1));

        assertThat(mvc.get().uri("/api/authors")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                        {
                          "content": [
                            {"id": 2, "name": "Frank Herbert", "birthYear": 1920},
                            {"id": 1, "name": "Jane Austen", "birthYear": 1775}
                          ],
                          "page": 0, "size": 20, "totalElements": 2, "totalPages": 1
                        }
                        """);
    }

    @Test
    void booksOfAnAuthor() {
        given(service.findBooks(1L))
                .willReturn(List.of(new AuthorBookResponse(3L, "Dune"), new AuthorBookResponse(4L, "Dune Messiah")));

        assertThat(mvc.get().uri("/api/authors/1/books"))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        [{"id": 3, "title": "Dune"}, {"id": 4, "title": "Dune Messiah"}]
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
        given(service.findById(99L)).willThrow(new NotFoundException("Author", 99L));

        // The service throws a domain exception; GlobalExceptionHandler (picked up by @WebMvcTest,
        // like every @ControllerAdvice) writes the ProblemDetail.
        assertThat(mvc.get().uri("/api/authors/99"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Author 99 not found",
                          "instance": "/api/authors/99"
                        }
                        """);
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
        willThrow(new NotFoundException("Author", 99L)).given(service).delete(99L);

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

    @Test
    void blankNameIs400WithAFieldError() {
        assertThat(mvc.post()
                        .uri("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "  ", "birthYear": 1934}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("name", List.of("must not be blank")));
        // @Valid runs before the method: the service is never called with invalid data.
        verifyNoInteractions(service);
    }

    @Test
    void messagesAreEnglishWhateverTheClientLocale() {
        // Spring passes the request locale (Accept-Language, else the JVM default: Spanish on a
        // Spanish Windows) to Hibernate Validator, which translates its built-in messages. Our custom
        // messages exist only in English, so application.yml pins the locale and every message matches.
        assertThat(mvc.post()
                        .uri("/api/authors")
                        .header("Accept-Language", "es")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors.name")
                .asArray()
                .containsExactly("must not be blank");
    }

    @Test
    void birthYearInTheFutureIs400() {
        assertThat(mvc.post()
                        .uri("/api/authors")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Time Traveller", "birthYear": 3000}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors.birthYear")
                .asArray()
                .containsExactly("must be a year between 0 and the current year");
        verifyNoInteractions(service);
    }

    @Test
    void nonPositiveIdIs400() {
        assertThat(mvc.get().uri("/api/authors/0"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("id", List.of("must be greater than 0")));
        verifyNoInteractions(service);
    }

    @Test
    void updateWithAnInvalidBodyReportsTheFieldErrors() {
        // The id carries @Positive, so method validation checks the body too and throws
        // HandlerMethodValidationException instead of MethodArgumentNotValidException. The client
        // sees the same "errors" shape either way.
        assertThat(mvc.put()
                        .uri("/api/authors/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": ""}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("name", List.of("must not be blank")));
        verifyNoInteractions(service);
    }
}
