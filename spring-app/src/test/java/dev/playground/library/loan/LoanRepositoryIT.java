package dev.playground.library.loan;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.config.JpaAuditingConfig;
import dev.playground.library.member.Member;
import dev.playground.library.member.Role;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * Loan queries against PostgreSQL, with Hibernate's statement counter on: the N+1 problem next to
 * its fix, the grouped count, and the filters. Guide: §5.5 Advanced JPA.
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class LoanRepositoryIT {

    private static final Instant T = Instant.parse("2026-09-01T10:00:00Z");
    private static final LocalDate DUE = LocalDate.of(2026, 9, 15);

    @Autowired
    private LoanRepository loans;

    @Autowired
    private TestEntityManager em;

    private Statistics statistics;

    private Book dune;
    private Book emma;
    private Book cosmos;
    private Member ada;
    private Member alan;

    @BeforeEach
    void saveLoans() {
        dune = em.persist(new Book("9780441013593", "Dune", 1965, 3));
        emma = em.persist(new Book("9780141439587", "Emma", 1815, 2));
        cosmos = em.persist(new Book("9780345539434", "Cosmos", 1980, 2));
        ada = em.persist(new Member("ada@library.test", "Ada Lovelace", null, Role.MEMBER));
        alan = em.persist(new Member("alan@library.test", "Alan Turing", null, Role.MEMBER));

        em.persist(new Loan(dune, ada, T, DUE));
        em.persist(new Loan(dune, alan, T, DUE));
        em.persist(new Loan(emma, ada, T, DUE));
        Loan returned = new Loan(cosmos, alan, T, DUE);
        returned.markReturned(T.plusSeconds(3_600));
        em.persist(returned);

        em.flush();
        em.clear();
        statistics = em.getEntityManager()
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
    }

    private long statements() {
        return statistics.getPrepareStatementCount();
    }

    @Test
    void withoutAnEntityGraphEachLoansBookIsAnotherQuery() {
        List<Loan> all = loans.findAll(); // the inherited method: no entity graph

        all.forEach(loan -> loan.getBook().getTitle()); // each lazy proxy loads on first use

        // 1 for the loans + 1 per distinct book (Dune, Emma, Cosmos): the N+1 problem.
        // (A book already loaded is not queried again: the persistence context holds it.)
        assertThat(statements()).isEqualTo(4);
    }

    @Test
    void theEntityGraphFetchesTheBooksInTheSameQuery() {
        List<Loan> all = loans.findAll(Specification.unrestricted(), Sort.by("id"));

        all.forEach(loan -> loan.getBook().getTitle());

        assertThat(statements()).isEqualTo(1);
    }

    @Test
    void theMemberIdOfALazyProxyNeedsNoQuery() {
        Loan loan = loans.findAll(Specification.unrestricted(), Sort.by("id")).getFirst();

        loan.getMember().getId(); // the proxy was built from loans.member_id

        assertThat(statements()).isEqualTo(1); // just the list query
    }

    @Test
    void activeLoansAreCountedPerBookInOneQuery() {
        List<ActiveLoanCount> counts = loans.countActiveByBookIds(List.of(dune.getId(), emma.getId(), cosmos.getId()));

        // Cosmos's only loan was returned, so it has no row at all.
        assertThat(counts)
                .containsExactlyInAnyOrder(new ActiveLoanCount(dune.getId(), 2), new ActiveLoanCount(emma.getId(), 1));
        assertThat(statements()).isEqualTo(1);
    }

    @Test
    void filtersCombine() {
        assertThat(loans.findAll(LoanSpecifications.ofMember(ada.getId()), Sort.by("id")))
                .extracting(loan -> loan.getBook().getTitle())
                .containsExactly("Dune", "Emma");
        assertThat(loans.findAll(
                        Specification.allOf(
                                LoanSpecifications.ofMember(alan.getId()), LoanSpecifications.active(false)),
                        Sort.by("id")))
                .extracting(loan -> loan.getBook().getTitle())
                .containsExactly("Cosmos");
        assertThat(loans.findAll(LoanSpecifications.active(true), Sort.by("id")))
                .hasSize(3);
    }
}
