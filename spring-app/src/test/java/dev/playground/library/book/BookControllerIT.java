package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * The whole stack, from HTTP to PostgreSQL: the real controller, service, repository, Flyway schema
 * and constraints. Nothing is mocked. Tests share one database, so each starts from empty tables.
 * Guide: §5.4 Persistence with JPA, §5.5 Advanced JPA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BookControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private BookRepository repository;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheTables() {
        TestTables.truncateAll(jdbc);
    }

    private MvcTestResult create(String isbn, String title, Long... authorIds) {
        return mvc.post()
                .uri("/api/books")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"isbn": "%s", "title": "%s", "publishedYear": 1965, "totalCopies": 3, "authorIds": %s}
                        """.formatted(isbn, title, Arrays.toString(authorIds)))
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
    void authorsAreLinkedFilteredAndReplaced() throws Exception {
        Author herbert = authors.save(new Author("Frank Herbert", 1920));
        Author leGuin = authors.save(new Author("Ursula K. Le Guin", 1929));
        long dune = idOf(create("9780441013593", "Dune", leGuin.getId(), herbert.getId()));
        create("9780061054884", "The Dispossessed", leGuin.getId());

        // The response lists the authors by name, whatever order the request used.
        assertThat(mvc.get().uri("/api/books/{id}", dune))
                .bodyJson()
                .extractingPath("$.authors[*].name")
                .asArray()
                .containsExactly("Frank Herbert", "Ursula K. Le Guin");

        assertThat(mvc.get().uri("/api/books").queryParam("authorId", String.valueOf(herbert.getId())))
                .bodyJson()
                .extractingPath("$.content[*].title")
                .asArray()
                .containsExactly("Dune");

        // PUT replaces the whole set: Le Guin is no longer an author of Dune.
        assertThat(mvc.put()
                        .uri("/api/books/{id}", dune)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune", "totalCopies": 3, "authorIds": [%d]}
                                """.formatted(herbert.getId())))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.authors[*].name")
                .asArray()
                .containsExactly("Frank Herbert");
        assertThat(jdbc.queryForObject("select count(*) from book_authors where book_id = ?", Long.class, dune))
                .isEqualTo(1);
    }

    @Test
    void anUnknownAuthorIdIs404AndNothingIsCreated() {
        assertThat(create("9780441013593", "Dune", 999L))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Author 999 not found");
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
    void listIsPagedAndFiltersByTitleIgnoringCase() {
        create("9780593098233", "Dune Messiah");
        create("9780141439587", "Emma");
        create("9780441013593", "Dune");

        assertThat(mvc.get().uri("/api/books").queryParam("title", "DUNE"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.content[*].title")
                .asArray()
                .containsExactly("Dune", "Dune Messiah");

        // Sorted by title by default; page 1 (zero-based) of size 2 holds the third title.
        assertThat(mvc.get().uri("/api/books").queryParam("page", "1").queryParam("size", "2"))
                .bodyJson()
                .isLenientlyEqualTo("""
                        {"content": [{"title": "Emma"}], "page": 1, "size": 2, "totalElements": 3, "totalPages": 2}
                        """);
    }

    @Test
    void sortingThroughARelationIs400() throws Exception {
        // authors.name exists, but sorting by it joins book_authors: a book with two authors
        // becomes two rows, and LIMIT/OFFSET would count rows, not books (duplicates, wrong totals).
        Author herbert = authors.save(new Author("Frank Herbert", 1920));
        create("9780441013593", "Dune", herbert.getId());

        assertThat(mvc.get().uri("/api/books").queryParam("sort", "authors.name"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Cannot sort by 'authors.name'. Sortable: id, isbn, publishedYear, title, totalCopies.");
        assertThat(mvc.get().uri("/api/authors").queryParam("sort", "books")).hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void anUnknownSortPropertyIs400() {
        assertThat(mvc.get().uri("/api/books").queryParam("sort", "popularity"))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Cannot sort by 'popularity'. Sortable: id, isbn, publishedYear, title, totalCopies.");
    }
}
