package dev.playground.library.loan;

/**
 * A DTO projection: one row of a grouped query, built by the JPQL constructor expression
 * {@code select new dev.playground.library.loan.ActiveLoanCount(...)} in {@link LoanRepository}. A
 * record works because Hibernate calls its constructor. Guide: §5.5 Advanced JPA.
 */
public record ActiveLoanCount(Long bookId, long activeLoans) {}
