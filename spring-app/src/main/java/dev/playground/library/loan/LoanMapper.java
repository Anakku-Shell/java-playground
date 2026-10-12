package dev.playground.library.loan;

import dev.playground.library.loan.dto.LoanResponse;

/** Manual mapping from {@link Loan} to its DTO. Guide: §5.5 Advanced JPA. */
public final class LoanMapper {

    private LoanMapper() {}

    /**
     * Needs the book loaded (its title), so call it inside the transaction or on a loan fetched with
     * its book. {@code getMember().getId()} is free: a lazy proxy knows its id without a query.
     */
    public static LoanResponse toResponse(Loan loan) {
        return new LoanResponse(
                loan.getId(),
                loan.getBook().getId(),
                loan.getBook().getTitle(),
                loan.getMember().getId(),
                loan.getLoanedAt(),
                loan.getDueDate(),
                loan.getReturnedAt());
    }
}
