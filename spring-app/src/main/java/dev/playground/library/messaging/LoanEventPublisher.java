package dev.playground.library.messaging;

import dev.playground.library.loan.LoanCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Publishes every new loan to the {@code library.loans} topic. It reacts to the same in-process
 * event as {@code LoanNotificationListener} (§5.9): {@code LoanService} knows nothing about Kafka.
 *
 * <p>{@code @TransactionalEventListener} (after commit): a loan that rolls back is never published.
 * The reverse gap remains: if the application stops between the commit and the send, or the broker
 * is down, the loan is in the database and not on the topic. The outbox pattern closes it (§5.10).
 *
 * <p>{@code send} does not wait for the broker: it buffers the record and returns a future, which
 * completes once the broker has it ({@code acks=all} by default: once every in-sync replica has it).
 * No {@code @Async} needed. The exception: with no metadata for the topic yet (the broker is
 * unreachable) or a full buffer, {@code send} blocks the calling thread for up to
 * {@code max.block.ms}, 5 s here ({@code application.yml}). Guide: §5.10 Kafka.
 */
@Component
@ConditionalOnBooleanProperty("library.kafka.enabled")
public class LoanEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoanEventPublisher.class);

    private final KafkaTemplate<String, LoanMessage> kafka;

    public LoanEventPublisher(KafkaTemplate<String, LoanMessage> kafka) {
        this.kafka = kafka;
    }

    @TransactionalEventListener
    public void onLoanCreated(LoanCreatedEvent event) {
        var message = new LoanMessage(event.loanId(), event.bookId(), event.memberId(), event.dueDate());
        // The key is the book id: Kafka hashes it to pick the partition, so the loans of one book stay
        // in order. A null key would spread them round the partitions, with no order at all.
        kafka.send(KafkaConfig.LOANS_TOPIC, event.bookId().toString(), message).whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Loan {} was not published to {}", event.loanId(), KafkaConfig.LOANS_TOPIC, ex);
            } else {
                log.debug("Loan {} published to {}", event.loanId(), result.getRecordMetadata());
            }
        });
    }
}
