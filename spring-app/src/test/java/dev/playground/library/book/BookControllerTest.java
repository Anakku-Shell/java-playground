package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.common.PageResponse;
import dev.playground.library.security.JwtConfig;
import dev.playground.library.security.SecurityConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/** The HTTP contract of {@link BookController}, with the service mocked. Guide: §5.2 REST API. */
@WebMvcTest(BookController.class)
// The slice loads controllers, not @Configuration classes: the real security rules are imported, and
// every test runs as a librarian unless it says otherwise (§5.7).
@Import({SecurityConfig.class, JwtConfig.class})
@WithMockUser(roles = "LIBRARIAN")
class BookControllerTest {

    private static final BookResponse DUNE = new BookResponse(1L, "9780441013593", "Dune", 1965, 3, 3, List.of());

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BookService service;

    private static PageResponse<BookResponse> onePage(BookResponse... books) {
        return new PageResponse<>(List.of(books), 0, 20, books.length, 1);
    }

    @Test
    void listReturnsTheFirstPageSortedByTitle() {
        // No paging parameters: page 0, size 20 (Spring Data's default), sort from @SortDefault.
        given(service.findAll(null, null, PageRequest.of(0, 20, Sort.by("title"))))
                .willReturn(onePage(DUNE));

        assertThat(mvc.get().uri("/api/books")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {
                  "content": [{"id": 1, "isbn": "9780441013593", "title": "Dune", "publishedYear": 1965,
                               "totalCopies": 3, "availableCopies": 3, "authors": []}],
                  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
                }
                """);
    }

    @Test
    void listPassesTheFiltersAndPagingParameters() {
        // Optional query parameters: absent, they arrive as null.
        var pageable = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "publishedYear"));
        given(service.findAll("dune", 7L, pageable)).willReturn(onePage(DUNE));

        assertThat(mvc.get()
                        .uri("/api/books")
                        .queryParam("title", "dune")
                        .queryParam("authorId", "7")
                        .queryParam("page", "2")
                        .queryParam("size", "5")
                        .queryParam("sort", "publishedYear,desc"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content[*].title")
                .asArray()
                .containsExactly("Dune");
    }

    @Test
    void aPageSizeAboveTheMaximumIsClamped() {
        // spring.data.web.pageable.max-page-size: 100. Not an error: the client gets 100 at most.
        given(service.findAll(null, null, PageRequest.of(0, 100, Sort.by("title"))))
                .willReturn(onePage(DUNE));

        assertThat(mvc.get().uri("/api/books").queryParam("size", "500")).hasStatusOk();
        verify(service).findAll(null, null, PageRequest.of(0, 100, Sort.by("title")));
    }

    @Test
    void anAuthorIdFilterMustBePositive() {
        assertThat(mvc.get().uri("/api/books").queryParam("authorId", "0"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("authorId", List.of("must be greater than 0")));
        verifyNoInteractions(service);
    }

    @Test
    void authorIdsMustBePositive() {
        // Constraints on the type argument (Set<@NotNull @Positive Long>) check every element.
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune", "totalCopies": 3, "authorIds": [0]}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("authorIds[]", List.of("must be greater than 0")));
        verifyNoInteractions(service);
    }

    @Test
    void getReturnsOneBook() {
        given(service.findById(1L)).willReturn(DUNE);

        assertThat(mvc.get().uri("/api/books/1"))
                .hasStatusOk()
                .hasContentType(MediaType.APPLICATION_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "id": 1,
                          "isbn": "9780441013593",
                          "title": "Dune",
                          "publishedYear": 1965,
                          "totalCopies": 3,
                          "availableCopies": 3,
                          "authors": []
                        }
                        """);
    }

    @Test
    void getMissingIs404() {
        given(service.findById(42L)).willThrow(new NotFoundException("Book", 42L));

        assertThat(mvc.get().uri("/api/books/42"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Not Found",
                          "status": 404,
                          "detail": "Book 42 not found",
                          "instance": "/api/books/42"
                        }
                        """);
    }

    @Test
    void createReturns201WithLocation() {
        var request = new CreateBookRequest("9780441013593", "Dune", 1965, 3, Set.of());
        given(service.create(request)).willReturn(DUNE);

        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune", "publishedYear": 1965,
                                 "totalCopies": 3, "authorIds": []}
                                """))
                .hasStatus(HttpStatus.CREATED)
                .hasHeader("Location", "http://localhost/api/books/1");
    }

    @Test
    void updateReturns200() {
        var request = new UpdateBookRequest("9780441013593", "Dune", 1965, 5, Set.of());
        var updated = new BookResponse(1L, "9780441013593", "Dune", 1965, 5, 5, List.of());
        given(service.update(1L, request)).willReturn(updated);

        assertThat(mvc.put()
                        .uri("/api/books/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune", "publishedYear": 1965,
                                 "totalCopies": 5, "authorIds": []}
                                """))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.totalCopies")
                .isEqualTo(5);
    }

    @Test
    void deleteReturns204() {
        assertThat(mvc.delete().uri("/api/books/1"))
                .hasStatus(HttpStatus.NO_CONTENT)
                .body()
                .isEmpty();
        verify(service).delete(1L);
    }

    @Test
    void deleteMissingIs404() {
        willThrow(new NotFoundException("Book", 42L)).given(service).delete(42L);

        assertThat(mvc.delete().uri("/api/books/42")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void malformedJsonIs400() {
        // Jackson cannot read the body, so Spring answers 400 before the controller method runs.
        // ResponseEntityExceptionHandler (GlobalExceptionHandler's base class) makes it a ProblemDetail.
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Dune\","))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Failed to read request");
        verifyNoInteractions(service);
    }

    @Test
    void missingPrimitiveIs400() {
        // totalCopies is an int. Jackson 3 enables FAIL_ON_NULL_FOR_PRIMITIVES by default, so a
        // missing (or null) value is rejected instead of silently becoming 0 as in Jackson 2.
        // This happens while reading the body, before @Valid can run, so there is no "errors" map:
        // the ProblemDetail only says "Failed to read request".
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune"}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void invalidIsbnIs400WithAFieldError() {
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013594", "title": "Dune", "totalCopies": 3}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("isbn", List.of("must be a valid ISBN-10 or ISBN-13")));
        verifyNoInteractions(service);
    }

    @Test
    void blankTitleIs400() {
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "", "totalCopies": 3}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("title", List.of("must not be blank")));
        verifyNoInteractions(service);
    }

    @Test
    void everyInvalidFieldIsReportedAtOnce() {
        // Validation does not stop at the first failure: the client can fix everything in one go.
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "", "title": "Dune", "publishedYear": 3000, "totalCopies": -1}
                                """))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of(
                        "isbn", List.of("must not be blank"),
                        "publishedYear", List.of("must be a year between 0 and the current year"),
                        "totalCopies", List.of("must be greater than or equal to 0")));
        verifyNoInteractions(service);
    }

    @Test
    void duplicateIsbnIs409() {
        var request = new CreateBookRequest("9780441013593", "Dune", 1965, 3, Set.of());
        given(service.create(request))
                .willThrow(new ConflictException("A book with ISBN 9780441013593 already exists"));

        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune", "publishedYear": 1965,
                                 "totalCopies": 3, "authorIds": []}
                                """))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A book with ISBN 9780441013593 already exists");
    }

    @Test
    void nonPositiveIdIs400() {
        assertThat(mvc.delete().uri("/api/books/-1"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.errors")
                .isEqualTo(Map.of("id", List.of("must be greater than 0")));
        verifyNoInteractions(service);
    }

    @Test
    void nonNumericIdIs400() {
        // "abc" cannot be converted to the Long path variable: a type mismatch, also 400.
        assertThat(mvc.get().uri("/api/books/abc")).hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void unsupportedAcceptIs406() {
        given(service.findById(1L)).willReturn(DUNE);

        // Content negotiation: the client asks for XML and no converter can write it. The mapping
        // declares no `produces`, so the method runs first and the 406 comes when the result is
        // written.
        assertThat(mvc.get().uri("/api/books/1").accept(MediaType.APPLICATION_XML))
                .hasStatus(HttpStatus.NOT_ACCEPTABLE);
        verify(service).findById(1L);
    }

    @Test
    void unmappedMethodIs405() {
        assertThat(mvc.patch().uri("/api/books/1")).hasStatus(HttpStatus.METHOD_NOT_ALLOWED);
        verifyNoInteractions(service);
    }

    @Test
    void wrongContentTypeIs415() {
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("Dune"))
                .hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        verifyNoInteractions(service);
    }

    // Security rules (§5.7), checked in the slice: the real SecurityFilterChain runs in front of the
    // controller, and @WithMockUser puts a user in the SecurityContext without any token.

    @Test
    @WithAnonymousUser
    void withoutATokenTheCatalogueIs401() {
        assertThat(mvc.get().uri("/api/books"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Authentication is required: send a bearer token.");
        verifyNoInteractions(service);
    }

    @Test
    @WithMockUser(roles = "MEMBER")
    void aMemberReadsTheCatalogueButCannotChangeIt() {
        given(service.findById(1L)).willReturn(DUNE);

        assertThat(mvc.get().uri("/api/books/1")).hasStatusOk();
        assertThat(mvc.delete().uri("/api/books/1"))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("You are not allowed to do this.");
        // The rule stopped the request before the controller: nothing was deleted.
        verify(service, never()).delete(1L);
    }
}
