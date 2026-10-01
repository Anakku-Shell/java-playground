package dev.playground.library.loan;

import java.time.LocalDate;

/**
 * "A loan was created": published by {@code LoanService.borrow}, inside its transaction. Any bean
 * can react to it without the service knowing who listens (today, {@code LoanNotificationListener};
 * tomorrow maybe statistics or a message broker). A record with ids and the few values a listener
 * needs, not the entity: a listener may run on another thread, after the persistence context is
 * gone. Guide: §5.9 Beyond CRUD.
 */
public record LoanCreatedEvent(Long loanId, Long bookId, String bookTitle, Long memberId, LocalDate dueDate) {}
