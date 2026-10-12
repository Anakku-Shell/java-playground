package dev.playground.library.messaging;

import java.time.LocalDate;

/**
 * A loan as it travels on the {@code library.loans} topic, as JSON. It is a contract with every
 * consumer, possibly other applications: it holds ids and plain values, never an entity, and its
 * fields change the way an API's do (add, do not rename). The Java class name does not travel (no
 * type header), so a consumer in another code base is not tied to this package. Guide: §5.10 Kafka.
 */
public record LoanMessage(Long loanId, Long bookId, Long memberId, LocalDate dueDate) {}
