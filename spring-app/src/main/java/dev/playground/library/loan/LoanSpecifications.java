package dev.playground.library.loan;

import org.springframework.data.jpa.domain.Specification;

/** The optional filters of {@code GET /api/loans} (see {@code BookSpecifications}). Guide: §5.5. */
public final class LoanSpecifications {

    private LoanSpecifications() {}

    public static Specification<Loan> ofMember(Long memberId) {
        if (memberId == null) {
            return Specification.unrestricted();
        }
        // member.id is the member_id column: no join to members.
        return (loan, query, cb) -> cb.equal(loan.get("member").get("id"), memberId);
    }

    /** {@code true}: not returned yet; {@code false}: returned; {@code null}: both. */
    public static Specification<Loan> active(Boolean active) {
        if (active == null) {
            return Specification.unrestricted();
        }
        return (loan, query, cb) -> active ? cb.isNull(loan.get("returnedAt")) : cb.isNotNull(loan.get("returnedAt"));
    }
}
