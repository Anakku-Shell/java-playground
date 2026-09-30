package dev.playground.library.loan;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/**
 * Loans. "Active" is {@code returnedAt IS NULL}, spelled {@code ReturnedAtIsNull} in derived
 * queries. Guide: §5.5 Advanced JPA.
 */
public interface LoanRepository extends JpaRepository<Loan, Long>, JpaSpecificationExecutor<Loan> {

    // "BookId" walks the relation to the id, which is the loans.book_id column itself: no join.
    long countByBookIdAndReturnedAtIsNull(Long bookId);

    long countByMemberIdAndReturnedAtIsNull(Long memberId);

    boolean existsByBookId(Long bookId);

    /**
     * Active loans for many books in one query, for a page of books (one count per book would be
     * N+1 again). Books with no active loan have no row.
     */
    @Query("""
            select new dev.playground.library.loan.ActiveLoanCount(l.book.id, count(l))
            from Loan l
            where l.book.id in :bookIds and l.returnedAt is null
            group by l.book.id""")
    List<ActiveLoanCount> countActiveByBookIds(Collection<Long> bookIds);

    /**
     * Overridden only to add the entity graph: every loan in the list needs its book's title, so the
     * books come in the same query (an inner join) instead of one query per loan. The inherited
     * {@code findAll()} has no graph; {@code LoanRepositoryIT} compares the two.
     */
    @Override
    @EntityGraph(attributePaths = "book")
    List<Loan> findAll(Specification<Loan> spec, Sort sort);
}
