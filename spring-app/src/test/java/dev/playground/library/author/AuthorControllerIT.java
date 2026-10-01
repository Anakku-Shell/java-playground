package dev.playground.library.author;

import static dev.playground.library.testing.TestDataFactory.herbert;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Authors from HTTP to PostgreSQL. Same setup as {@code BookControllerIT}, so Spring reuses its
 * cached context (and container). Guide: §5.4 Persistence with JPA, §5.5 Advanced JPA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
// Security is not the subject here (§5.7 has its own tests): every request runs as a librarian.
@WithMockUser(roles = "LIBRARIAN")
class AuthorControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private AuthorRepository repository;

    @Autowired
    private BookRepository books;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTheTables() {
        TestTables.truncateAll(jdbc);
    }

    private Book bookBy(Author author, String isbn, String title) {
        Book book = new Book(isbn, title, 1965, 1);
        book.replaceAuthors(Set.of(author));
        return books.save(book);
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
                        {
                          "content": [{"id": %d, "name": "Carl Sagan", "birthYear": 1934}],
                          "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
                        }
                        """.formatted(id));

        assertThat(mvc.delete().uri("/api/authors/{id}", id)).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(repository.existsById(id)).isFalse();
    }

    @Test
    void deletingAuthorWithBooksReturns409() {
        Author herbert = repository.save(herbert());
        bookBy(herbert, "9780441013593", "Dune");

        assertThat(mvc.delete().uri("/api/authors/{id}", herbert.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Author " + herbert.getId() + " has books and cannot be deleted");
        assertThat(repository.existsById(herbert.getId())).isTrue();
    }

    @Test
    void theBooksOfAnAuthorByTitle() {
        Author herbert = repository.save(herbert());
        Book messiah = bookBy(herbert, "9780593098233", "Dune Messiah");
        Book dune = bookBy(herbert, "9780441013593", "Dune");

        assertThat(mvc.get().uri("/api/authors/{id}/books", herbert.getId()))
                .hasStatusOk()
                .bodyJson()
                .isStrictlyEqualTo("""
                        [{"id": %d, "title": "Dune"}, {"id": %d, "title": "Dune Messiah"}]
                        """.formatted(dune.getId(), messiah.getId()));
        assertThat(mvc.get().uri("/api/authors/{id}/books", 999)).hasStatus(HttpStatus.NOT_FOUND);
    }
}
