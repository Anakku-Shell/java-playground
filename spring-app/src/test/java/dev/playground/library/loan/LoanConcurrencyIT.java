package dev.playground.library.loan;

import static dev.playground.library.testing.TestDataFactory.ada;
import static dev.playground.library.testing.TestDataFactory.alan;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestTables;
import dev.playground.library.TestUsers;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Two requests racing for the same rule, forced to overlap. Each request checks a rule (a copy is
 * free, the member is under the maximum, the loan is still active) and then writes. Run one after
 * the other, the second sees the first one's write and gets a 409 from the check, so the race needs
 * both checks to happen before either write. The test makes sure of it by holding a lock on a
 * table that lets reads through and blocks writes. Guide: §5.6 Transactions.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoanConcurrencyIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private BookRepository books;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    private final ExecutorService pool = Executors.newFixedThreadPool(2);

    private Book lastCopy;
    private Member ada;
    private Member alan;

    @BeforeEach
    void aBookWithOneCopyAndTwoMembers() {
        TestTables.truncateAll(jdbc);
        lastCopy = books.save(new Book("9780061054884", "The Dispossessed", 1974, 1));
        ada = members.save(ada());
        alan = members.save(alan());
    }

    @AfterEach
    void stopThreads() {
        pool.shutdownNow();
    }

    @Test
    void onlyOneBorrowWinsForLastCopy() throws Exception {
        List<Integer> statuses = race("loans", () -> borrow(lastCopy, ada), () -> borrow(lastCopy, alan));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(activeLoans()).isEqualTo(1);
    }

    @Test
    void aMemberCannotPassTheMaximumWithTwoBorrowsAtOnce() throws Exception {
        // library.loans.max-active is 3: Ada holds 2, so one more fits, not two. The books differ, so
        // their versions do not collide; the member's version does.
        borrow(books.save(new Book("9780593098233", "Dune Messiah", 1969, 1)), ada);
        borrow(books.save(new Book("9780141439587", "Emma", 1815, 1)), ada);
        Book cosmos = books.save(new Book("9780345539434", "Cosmos", 1980, 1));

        List<Integer> statuses = race("loans", () -> borrow(lastCopy, ada), () -> borrow(cosmos, ada));

        assertThat(statuses).containsExactlyInAnyOrder(201, 409);
        assertThat(activeLoans()).isEqualTo(3);
    }

    @Test
    void anEditAndABorrowOfTheSameBookCollide() throws Exception {
        // The edit writes the book row; the borrow bumps its version. Whichever commits second
        // finds a new version and fails, so no borrow slips past a totalCopies it never saw.
        List<Integer> statuses = race(
                "books",
                () -> borrow(lastCopy, ada),
                () -> mvc.put()
                        .uri("/api/books/{id}", lastCopy.getId())
                        .with(TestUsers.librarian())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                        {"isbn": "9780061054884", "title": "The Dispossessed", "totalCopies": 2}
                        """)
                        .exchange());

        assertThat(statuses).contains(409).containsAnyOf(200, 201).hasSize(2);
    }

    @Test
    void onlyOneReturnWins() throws Exception {
        long loan = idOf(borrow(lastCopy, ada));

        List<MvcTestResult> results = raceForResults("loans", () -> giveBack(loan), () -> giveBack(loan));

        assertThat(results)
                .extracting(result -> result.getResponse().getStatus())
                .containsExactlyInAnyOrder(200, 409);
        // The loser wrote nothing: returnedAt is the one the winner answered with.
        MvcTestResult winner = results.stream()
                .filter(result -> result.getResponse().getStatus() == 200)
                .findFirst()
                .orElseThrow();
        Instant answered = Instant.parse(JsonPath.read(winner.getResponse().getContentAsString(), "$.returnedAt"));
        assertThat(jdbc.queryForObject("select returned_at from loans where id = ?", Timestamp.class, loan)
                        .toInstant())
                .isEqualTo(answered);
    }

    private List<Integer> race(String table, Callable<MvcTestResult> first, Callable<MvcTestResult> second)
            throws Exception {
        return raceForResults(table, first, second).stream()
                .map(result -> result.getResponse().getStatus())
                .toList();
    }

    /**
     * Runs both requests while this test holds {@code LOCK TABLE <table> IN EXCLUSIVE MODE} in a
     * transaction of its own: PostgreSQL still lets both requests read the table (their checks
     * pass), but their first write to it waits. Once both wait, the test commits, which releases
     * the lock, and the two requests race to commit. A request that finishes while the lock is held
     * never reached its write (a 404, or a check that already failed): the test says so at once.
     *
     * <p>The requests run on pool threads, so they carry their caller themselves ({@code TestUsers}):
     * {@code @WithMockUser} would set it on the test thread only.
     */
    private List<MvcTestResult> raceForResults(
            String table, Callable<MvcTestResult> first, Callable<MvcTestResult> second) throws Exception {
        try (Connection lockHolder = dataSource.getConnection();
                Statement statement = lockHolder.createStatement()) {
            lockHolder.setAutoCommit(false);
            statement.execute("LOCK TABLE " + table + " IN EXCLUSIVE MODE");

            Future<MvcTestResult> a = pool.submit(first);
            Future<MvcTestResult> b = pool.submit(second);
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> a.isDone() || b.isDone() || sessionsWaitingOn(table) == 2);
            assertThat(a.isDone() || b.isDone())
                    .as("a request finished before its write, so there was no race")
                    .isFalse();

            lockHolder.commit();
            return List.of(a.get(10, SECONDS), b.get(10, SECONDS));
        }
    }

    /** Sessions blocked on a lock of the table: pg_locks rows that are not granted yet. */
    private int sessionsWaitingOn(String table) {
        return jdbc.queryForObject(
                "select count(*) from pg_locks where relation = ?::regclass and not granted", Integer.class, table);
    }

    private long activeLoans() {
        return jdbc.queryForObject("select count(*) from loans where returned_at is null", Long.class);
    }

    private MvcTestResult borrow(Book book, Member member) {
        return mvc.post()
                .uri("/api/loans")
                .with(TestUsers.member(member))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId": %d, "memberId": %d}
                        """.formatted(book.getId(), member.getId()))
                .exchange();
    }

    private MvcTestResult giveBack(long loan) {
        return mvc.post()
                .uri("/api/loans/{id}/return", loan)
                .with(TestUsers.librarian())
                .exchange();
    }

    private static long idOf(MvcTestResult result) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }
}
