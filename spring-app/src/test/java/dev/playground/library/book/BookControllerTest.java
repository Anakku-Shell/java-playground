package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.web.server.ResponseStatusException;

/** The HTTP contract of {@link BookController}, with the service mocked. Guide: §5.2 REST API. */
@WebMvcTest(BookController.class)
class BookControllerTest {

    private static final BookResponse DUNE = new BookResponse(1L, "9780441013593", "Dune", 1965, 3, 3, List.of());

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private BookService service;

    @Test
    void listReturnsAllBooks() {
        given(service.findAll()).willReturn(List.of(DUNE));

        assertThat(mvc.get().uri("/api/books"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].title")
                .asArray()
                .containsExactly("Dune");
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
        given(service.findById(42L)).willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));

        assertThat(mvc.get().uri("/api/books/42")).hasStatus(HttpStatus.NOT_FOUND);
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
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND))
                .given(service)
                .delete(42L);

        assertThat(mvc.delete().uri("/api/books/42")).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void malformedJsonIs400() {
        // Jackson cannot read the body, so Spring answers 400 before the controller method runs.
        assertThat(mvc.post()
                        .uri("/api/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\": \"Dune\","))
                .hasStatus(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(service);
    }

    @Test
    void missingPrimitiveIs400() {
        // totalCopies is an int. Jackson 3 enables FAIL_ON_NULL_FOR_PRIMITIVES by default, so a
        // missing (or null) value is rejected instead of silently becoming 0 as in Jackson 2.
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
}
