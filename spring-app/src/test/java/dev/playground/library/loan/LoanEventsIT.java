package dev.playground.library.loan;

import static dev.playground.library.testing.TestDataFactory.ada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.loan.dto.LoanResponse;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import java.time.Duration;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@code LoanCreatedEvent} and its listener, with a real transaction: the notification is sent
 * after the commit, on another thread, and never for a rolled-back transaction. The listener only
 * logs, so the test reads the log ({@code OutputCaptureExtension}), and waits for it with
 * Awaitility: the listener is {@code @Async}, so the line arrives a moment after the commit.
 *
 * <p>Same setup as the other MockMvc ITs, so it reuses their Spring context (§5.8). Member ids 42
 * and up are not real members: the listener does not look them up. Guide: §5.9 Beyond CRUD.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class LoanEventsIT {

    @Autowired
    private LoanService loans;

    @Autowired
    private BookRepository books;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private ApplicationEventPublisher events;

    private Book book;
    private Member ada;

    @BeforeEach
    void aBookAndAMember() {
        TestTables.truncateAll(jdbc);
        // A title no other test uses: a late notification from another test class cannot match.
        book = books.save(new Book("9780441013593", "Events Test Book", 2026, 1));
        ada = members.save(ada());
    }

    private static LoanCreatedEvent loanFor(long memberId) {
        return new LoanCreatedEvent(1L, 1L, "Dune", memberId, LocalDate.of(2026, 10, 14));
    }

    @Test
    void aBorrowNotifiesTheMemberAfterTheCommitOnAnotherThread(CapturedOutput output) {
        LoanResponse loan = loans.borrow(book.getId(), ada.getId());

        String expected = "Notify member " + ada.getId() + ": 'Events Test Book' is due on " + loan.dueDate();
        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(output).contains(expected));
        // The log line's thread column, padded to 15 characters: "[        async-1]", a thread of the
        // executor in AsyncConfig, not the test's thread.
        assertThat(output.getAll().lines().filter(line -> line.contains(expected)))
                .singleElement()
                .asString()
                .containsPattern("\\[ *async-\\d+]");
    }

    @Test
    void aRolledBackTransactionNotifiesNobody(CapturedOutput output) {
        transaction.executeWithoutResult(status -> {
            events.publishEvent(loanFor(42));
            status.setRollbackOnly(); // what an exception after the publish would do
        });

        // "during": the condition must hold for the whole half second, not just once.
        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(output).doesNotContain("Notify member 42"));
    }

    @Test
    void aCommittedTransactionNotifiesOnceItCommits(CapturedOutput output) {
        transaction.executeWithoutResult(status -> events.publishEvent(loanFor(43)));

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(output).contains("Notify member 43"));
    }

    @Test
    void anEventPublishedOutsideATransactionIsDropped(CapturedOutput output) {
        // A @TransactionalEventListener waits for a commit; with no transaction there is none, and by
        // default the event is ignored (fallbackExecution = true would run it at once).
        events.publishEvent(loanFor(44));

        await().during(Duration.ofMillis(500))
                .atMost(Duration.ofSeconds(1))
                .untilAsserted(() -> assertThat(output).doesNotContain("Notify member 44"));
    }
}
