package dev.playground.library.book;

import static dev.playground.library.testing.TestDataFactory.herbert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.zaxxer.hikari.HikariDataSource;
import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import dev.playground.library.openlibrary.OpenLibraryStub;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The whole import, from HTTP to PostgreSQL: controller, {@code BookImportService}, the real HTTP
 * client against a local stand-in for Open Library ({@link OpenLibraryStub}), then
 * {@code TransactionTemplate} and the repositories. {@code BookImportServiceTest} checks the rules
 * with mocks; this checks that the pieces fit, and that the remote calls run outside any
 * transaction: while the stub answers, the connection pool must have no connection in use.
 *
 * <p>Its own Spring context (and PostgreSQL container): the Open Library URL points at the stub, a
 * property no other test sets (§5.8). Guide: §5.9 Beyond CRUD, §5.6 Transactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@WithMockUser(roles = "LIBRARIAN")
class BookImportIT {

    private static final OpenLibraryStub openLibrary = OpenLibraryStub.start();

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private CacheManager caches;

    @DynamicPropertySource
    static void pointAtTheStub(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.openlibrary.base-url", openLibrary::baseUrl);
    }

    @AfterAll
    static void stopTheStub() {
        openLibrary.close();
    }

    @BeforeEach
    void duneOnOpenLibrary() {
        TestTables.truncateAll(jdbc);
        // Open Library answers are cached by ISBN (§5.9) for the whole context: without this, only
        // the first test to import Dune would reach the stub.
        caches.getCache("open-library").clear();
        openLibrary.reset();
        openLibrary.redirect("/isbn/9780441013593.json", "/books/OL7524304M.json");
        openLibrary.json("/books/OL7524304M.json", """
                {"title": "Dune", "authors": [{"key": "/authors/OL79034A"}], "publish_date": "2005"}""");
        openLibrary.json("/authors/OL79034A.json", """
                {"name": "Frank Herbert", "birth_date": "8 October 1920"}""");
    }

    @Test
    void importsTheBookAndCreatesItsAuthor() {
        assertThat(mvc.post().uri("/api/books/import/0441013597")) // ISBN-10, stored as ISBN-13
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.authors[0].name")
                .isEqualTo("Frank Herbert");

        Book saved = books.findAll().getFirst();
        assertThat(saved.getIsbn()).isEqualTo("9780441013593");
        assertThat(saved.getPublishedYear()).isEqualTo(2005);
        assertThat(saved.getTotalCopies()).isEqualTo(1);
        assertThat(authors.findAll())
                .extracting(Author::getName, Author::getBirthYear)
                .containsExactly(tuple("Frank Herbert", 1920));
        assertThat(jdbc.queryForObject("select count(*) from book_authors", Long.class))
                .isEqualTo(1);
    }

    @Test
    void anAuthorAlreadyInTheCatalogueIsReusedWhateverTheCase() {
        Author existing = authors.save(herbert());
        openLibrary.json("/authors/OL79034A.json", """
                {"name": "FRANK HERBERT"}""");
        AtomicInteger remoteRequests = new AtomicInteger();
        openLibrary.onRequest(remoteRequests::incrementAndGet);

        assertThat(mvc.post().uri("/api/books/import/9780441013593")).hasStatus(HttpStatus.CREATED);

        assertThat(remoteRequests).as("the import reached the stub").hasValue(3);
        assertThat(authors.findAll()).extracting(Author::getId).containsExactly(existing.getId());
    }

    @Test
    void theRemoteCallsHoldNoDatabaseConnection() throws SQLException {
        var pool = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean();
        List<Integer> inUseDuringRemoteCalls = new CopyOnWriteArrayList<>();
        openLibrary.onRequest(() -> inUseDuringRemoteCalls.add(pool.getActiveConnections()));

        assertThat(mvc.post().uri("/api/books/import/9780441013593")).hasStatus(HttpStatus.CREATED);

        // The ISBN redirect, the edition and the author: three remote requests, none of them made
        // while a transaction held a connection. @Transactional on importBook would show a 1 here.
        assertThat(inUseDuringRemoteCalls).hasSize(3).containsOnly(0);
    }
}
