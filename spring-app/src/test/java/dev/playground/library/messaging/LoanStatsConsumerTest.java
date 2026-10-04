package dev.playground.library.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.messaging.dto.BookLoanCount;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * The consumer's logic, called directly: no Kafka. {@code LoanKafkaIT} checks the same through a
 * real broker. Guide: §5.10 Kafka.
 */
class LoanStatsConsumerTest {

    private final LoanStatsConsumer stats = new LoanStatsConsumer();

    private static LoanMessage loan(long loanId, long bookId) {
        return new LoanMessage(loanId, bookId, 1L, LocalDate.of(2026, 10, 14));
    }

    @Test
    void countsTheLoansOfEachBook() {
        stats.onLoan(loan(1, 10));
        stats.onLoan(loan(2, 10));
        stats.onLoan(loan(3, 20));

        assertThat(stats.loansOf(10)).isEqualTo(2);
        assertThat(stats.loansOf(20)).isEqualTo(1);
        assertThat(stats.loansOf(30)).isZero();
    }

    @Test
    void aMessageDeliveredTwiceIsCountedOnce() {
        // Kafka delivers at least once: after a crash or a rebalance, a message can come again.
        stats.onLoan(loan(1, 10));
        stats.onLoan(loan(1, 10));

        assertThat(stats.loansOf(10)).isEqualTo(1);
    }

    @Test
    void remembersWhichLoansItHasSeen() {
        stats.onLoan(loan(1, 10));

        assertThat(stats.hasCounted(1)).isTrue();
        assertThat(stats.hasCounted(2)).isFalse();
    }

    @Test
    void theRankingPutsTheMostBorrowedBookFirst() {
        stats.onLoan(loan(1, 20));
        stats.onLoan(loan(2, 10));
        stats.onLoan(loan(3, 30));
        stats.onLoan(loan(4, 30));

        // Ties by book id, so the order never depends on the map's.
        assertThat(stats.ranking())
                .containsExactly(new BookLoanCount(30L, 2), new BookLoanCount(10L, 1), new BookLoanCount(20L, 1));
    }
}
