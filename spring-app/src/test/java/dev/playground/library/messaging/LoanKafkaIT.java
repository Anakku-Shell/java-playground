package dev.playground.library.messaging;

import static dev.playground.library.testing.TestDataFactory.ada;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.playground.library.KafkaTestcontainersConfiguration;
import dev.playground.library.TestTables;
import dev.playground.library.TestUsers;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.loan.LoanCreatedEvent;
import dev.playground.library.loan.LoanService;
import dev.playground.library.loan.dto.LoanResponse;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loans through a real Kafka broker (a Testcontainer): {@code LoanEventPublisher} sends after the
 * commit, {@code LoanStatsConsumer} reads and counts. Every check waits with Awaitility, since the
 * message travels to the broker and back on other threads.
 *
 * <p>To show that something did <em>not</em> happen (a rolled-back loan, a counted duplicate), each
 * test sends a last message with the same key and waits until the consumer has read that one
 * ({@code hasCounted}). Same key, same partition, read in order: by then everything sent before it
 * has been read too, so the count can be checked once, exactly.
 *
 * <p>The consumer's counts and seen loan ids live in memory and survive between tests, while
 * {@code truncateAll} restarts the database ids. So the messages sent by hand use ids of their own
 * (books 8001..., loans 9001...), and only one test borrows for real: a second real borrow would get
 * loan id 1 again, and the consumer would take it for a duplicate. Its own Spring context: Kafka on,
 * plus a broker container (§5.8). Guide: §5.10 Kafka.
 */
@SpringBootTest(properties = "library.kafka.enabled=true")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, KafkaTestcontainersConfiguration.class})
class LoanKafkaIT {

    private static final Duration DELIVERY = Duration.ofSeconds(15);

    @Autowired
    private LoanService loanService;

    @Autowired
    private LoanStatsConsumer stats;

    @Autowired
    private KafkaTemplate<String, LoanMessage> kafka;

    @Autowired
    private KafkaConnectionDetails broker;

    @Autowired
    private BookRepository books;

    @Autowired
    private MemberRepository members;

    @Autowired
    private ApplicationEventPublisher events;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvcTester mvc;

    @BeforeEach
    void emptyTheTables() {
        TestTables.truncateAll(jdbc);
    }

    private static LoanMessage loan(long loanId, long bookId) {
        return new LoanMessage(loanId, bookId, 1L, LocalDate.of(2026, 10, 14));
    }

    /** Waits until the consumer has read the message of this loan. */
    private void awaitRead(long loanId) {
        await().atMost(DELIVERY).until(() -> stats.hasCounted(loanId));
    }

    @Test
    void aBorrowReachesTheConsumerAndTheStatsEndpoint() {
        Book dune = books.save(new Book("9780441013593", "Dune", 1965, 2));
        Member ada = members.save(ada());
        long before = stats.loansOf(dune.getId());

        LoanResponse loan = loanService.borrow(dune.getId(), ada.getId());

        awaitRead(loan.id());
        assertThat(stats.loansOf(dune.getId())).isEqualTo(before + 1);
        assertThat(mvc.get().uri("/api/stats/loans").with(TestUsers.librarian()))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[?(@.bookId == %d)].loans".formatted(dune.getId()))
                .asArray()
                .containsExactly((int) (before + 1));
    }

    @Test
    void aRolledBackLoanIsNeverPublished() {
        transaction.executeWithoutResult(status -> {
            events.publishEvent(new LoanCreatedEvent(9001L, 8001L, "Dune", 1L, LocalDate.of(2026, 10, 14)));
            status.setRollbackOnly();
        });
        transaction.executeWithoutResult(status ->
                events.publishEvent(new LoanCreatedEvent(9002L, 8001L, "Dune", 1L, LocalDate.of(2026, 10, 14))));

        awaitRead(9002);
        assertThat(stats.hasCounted(9001)).isFalse();
        assertThat(stats.loansOf(8001)).isEqualTo(1); // only the committed one
    }

    @Test
    void aMessageDeliveredTwiceIsCountedOnce() {
        kafka.send(KafkaConfig.LOANS_TOPIC, "8002", loan(9003, 8002));
        kafka.send(KafkaConfig.LOANS_TOPIC, "8002", loan(9003, 8002)); // a redelivery
        kafka.send(KafkaConfig.LOANS_TOPIC, "8002", loan(9004, 8002));

        awaitRead(9004);
        assertThat(stats.loansOf(8002)).isEqualTo(2);
    }

    @Test
    void aMessageThatIsNotJsonIsSkippedAndTheNextOneIsRead() {
        // Another producer, writing plain strings: what a buggy application might put on the topic.
        var producer = new DefaultKafkaProducerFactory<>(
                Map.<String, Object>of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBootstrapServers()),
                new StringSerializer(),
                new StringSerializer());
        try {
            new KafkaTemplate<>(producer).send(KafkaConfig.LOANS_TOPIC, "8003", "not json");
        } finally {
            producer.destroy(); // flushes the record, then closes the producer
        }
        kafka.send(KafkaConfig.LOANS_TOPIC, "8003", loan(9005, 8003));

        // Without the ErrorHandlingDeserializer, the consumer would fail on "not json" forever.
        awaitRead(9005);
        assertThat(stats.loansOf(8003)).isEqualTo(1);
    }

    @Test
    void theStatsAreForLibrarians() {
        Member ada = members.save(ada());

        assertThat(mvc.get().uri("/api/stats/loans").with(TestUsers.member(ada)))
                .hasStatus(HttpStatus.FORBIDDEN);
    }
}
