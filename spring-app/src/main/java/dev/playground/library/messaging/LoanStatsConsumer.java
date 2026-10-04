package dev.playground.library.messaging;

import dev.playground.library.messaging.dto.BookLoanCount;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads the {@code library.loans} topic and counts the loans of each book: a stand-in for any
 * service that reacts to loans without the loan code calling it (statistics, recommendations...).
 *
 * <p>{@code groupId}: the consumers of one group share the topic's partitions, and Kafka remembers
 * how far the group has read (its committed offsets). Another group, say {@code library-mail},
 * would get every message too, independently. Kafka delivers <em>at least once</em>: a message
 * processed but not yet committed comes again after a crash or a rebalance. So the consumer is
 * idempotent: it remembers the loan ids it has counted.
 *
 * <p>In memory, to keep the example small: a restart empties the counts, while the group's offsets
 * say the old messages were read. A real one would keep the counts and the seen ids in its own
 * database, the id under a unique constraint. Guide: §5.10 Kafka.
 */
@Component
@ConditionalOnBooleanProperty("library.kafka.enabled")
public class LoanStatsConsumer {

    // Written by the listener thread, read by request threads. The seen ids and the counts change
    // together under this object's lock, so a reader never sees a loan as seen but not yet counted.
    private final Set<Long> countedLoans = new HashSet<>();
    private final Map<Long, Long> loansPerBook = new HashMap<>();

    /**
     * Called by a listener container thread for each record, after the JSON deserializer has turned
     * its value into a {@link LoanMessage}. Returning normally lets the container commit the offset;
     * throwing makes its error handler retry the record, then log and skip it.
     */
    @KafkaListener(topics = KafkaConfig.LOANS_TOPIC, groupId = "library-stats")
    public synchronized void onLoan(LoanMessage message) {
        if (countedLoans.add(message.loanId())) {
            loansPerBook.merge(message.bookId(), 1L, Long::sum);
        }
    }

    /** Whether this loan's message has been read (and counted, the first time). */
    public synchronized boolean hasCounted(long loanId) {
        return countedLoans.contains(loanId);
    }

    public synchronized long loansOf(long bookId) {
        return loansPerBook.getOrDefault(bookId, 0L);
    }

    /** Most borrowed first; ties by book id. */
    public synchronized List<BookLoanCount> ranking() {
        return loansPerBook.entrySet().stream()
                .map(entry -> new BookLoanCount(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingLong(BookLoanCount::loans)
                        .reversed()
                        .thenComparing(BookLoanCount::bookId))
                .toList();
    }
}
