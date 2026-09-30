package dev.playground.library.loan.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * What the API returns for a loan. Jackson writes {@code Instant} as an ISO-8601 UTC string
 * ({@code "2026-09-30T10:00:00Z"}) and {@code LocalDate} as {@code "2026-10-14"}.
 * {@code returnedAt} is null while the loan is active. Guide: §5.5 Advanced JPA.
 */
public record LoanResponse(
        Long id,
        Long bookId,
        String bookTitle,
        Long memberId,
        Instant loanedAt,
        LocalDate dueDate,
        Instant returnedAt) {}
