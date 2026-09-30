package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestcontainersConfiguration;
import java.io.UnsupportedEncodingException;
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
 * The whole stack, from HTTP to PostgreSQL: the real controller, service, repository, Flyway schema
 * and constraints. Nothing is mocked. Tests share one database, so each starts from empty tables.
 * Guide: §5.4 Persistence with JPA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private BookRepository repository;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAll();
    }

    private MvcTestResult create(String isbn, String title) {
        return mvc.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isbn": "%s", "title": "%s", "publishedYear": 1965, "totalCopies": 3}
                        """.formatted(isbn, title))
                .exchange();
    }

    private static long idOf(MvcTestResult result) throws UnsupportedEncodingException {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void createReadUpdateDeleteRoundTrip() throws Exception {
        MvcTestResult created = create("9780441013593", "Dune");
        assertThat(created).hasStatus(HttpStatus.CREATED);
        long id = idOf(created);

        assertThat(mvc.get().uri("/api/books/{id}", id))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.title")
                .isEqualTo("Dune");

        assertThat(mvc.put()
                        .uri("/api/books/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune (40th anniversary)", "totalCopies": 5}
                                """))
                .hasStatusOk();
        // Read back through the repository: the UPDATE really reached the table (dirty checking).
        assertThat(repository.findById(id))
                .get()
                .extracting(Book::getTotalCopies)
                .isEqualTo(5);

        assertThat(mvc.delete().uri("/api/books/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(mvc.get().uri("/api/books/{id}", id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(repository.count()).isZero();
    }

    @Test
    void duplicateIsbnReturns409() {
        assertThat(create("9780441013593", "Dune")).hasStatus(HttpStatus.CREATED);

        // The ISBN-10 form of the same book.
        assertThat(create("0-441-01359-7", "Dune (again)"))
                .hasStatus(HttpStatus.CONFLICT)
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A book with ISBN 9780441013593 already exists");
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void updateOntoAnotherBooksIsbnReturns409FromTheServiceCheck() throws Exception {
        create("9780441013593", "Dune");
        long emma = idOf(create("9780141439587", "Emma"));

        // The service's own message, not the generic one from the unique constraint: the check ran
        // before the entity changed, so Hibernate had nothing to flush early (BookService.update).
        assertThat(mvc.put()
                        .uri("/api/books/{id}", emma)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Emma", "totalCopies": 2}
                                """))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("A book with ISBN 9780441013593 already exists");
        assertThat(repository.findById(emma)).get().extracting(Book::getIsbn).isEqualTo("9780141439587");
    }

    @Test
    void listFiltersByTitleIgnoringCase() {
        create("9780441013593", "Dune");
        create("9780593098233", "Dune Messiah");
        create("9780141439587", "Emma");

        assertThat(mvc.get().uri("/api/books").queryParam("title", "DUNE"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].title")
                .asArray()
                .containsExactly("Dune", "Dune Messiah");
        assertThat(mvc.get().uri("/api/books"))
                .bodyJson()
                .extractingPath("$[*].title")
                .asArray()
                .containsExactly("Dune", "Dune Messiah", "Emma");
    }
}
