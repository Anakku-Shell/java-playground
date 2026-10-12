package dev.playground.library.notification;

import dev.playground.library.loan.LoanCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tells a member about their new loan. Here it only logs; a real one would send an email.
 *
 * <p>Two annotations, two decisions:
 * <ul>
 *   <li>{@code @TransactionalEventListener} (phase {@code AFTER_COMMIT} by default): the event was
 *       published inside the borrow's transaction, and this runs only once that transaction has
 *       committed. If the borrow rolls back, the member is never told about a loan that does not
 *       exist. A plain {@code @EventListener} would run at once, inside the transaction, before
 *       anyone knows whether it will commit.
 *   <li>{@code @Async}: on another thread ({@code AsyncConfig}), so a slow mail server does not
 *       delay the response to the borrow. (A failure here could not undo the borrow anyway: an
 *       {@code AFTER_COMMIT} listener runs after the commit, and its exceptions are only logged.)
 * </ul>
 * The cost: if the application stops between the commit and this method, the notification is
 * lost. When it must not be, the event goes to a table in the same transaction and is sent from
 * there (the outbox pattern). Guide: §5.9 Beyond CRUD.
 */
@Component
public class LoanNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(LoanNotificationListener.class);

    @Async
    @TransactionalEventListener
    public void onLoanCreated(LoanCreatedEvent event) {
        log.info("Notify member {}: '{}' is due on {}", event.memberId(), event.bookTitle(), event.dueDate());
    }
}
