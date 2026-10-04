package dev.playground.library.messaging.dto;

/** One row of {@code GET /api/stats/loans}: how many loans a book has had. Guide: §5.10 Kafka. */
public record BookLoanCount(Long bookId, long loans) {}
