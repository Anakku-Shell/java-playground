package dev.playground.library.loan.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Body of {@code POST /api/loans}. The member comes from the token (§5.7): leave {@code memberId}
 * out to borrow for yourself. A librarian may set it to borrow on a member's behalf; a member who
 * sets someone else's id gets a 403. Guide: §5.5 Advanced JPA, §5.7 Security.
 */
public record CreateLoanRequest(
        @NotNull @Positive Long bookId, @Positive Long memberId) {}
