package dev.playground.library.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/loans}. From §5.7 on, the member comes from the token instead.
 * Guide: §5.5 Advanced JPA.
 */
public record CreateLoanRequest(
        @NotNull @Positive Long bookId, @NotNull @Positive Long memberId) {}
