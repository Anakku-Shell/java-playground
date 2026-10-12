package dev.playground.library.loan;

import static dev.playground.library.testing.TestDataFactory.ada;
import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.loan.dto.LoanResponse;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

/**
 * The pool trap of §5.6: a transaction that asks for a second connection ({@code REQUIRES_NEW})
 * while holding its own. With a pool of one connection, a borrow or return that still did that
 * would wait for the pool's timeout and fail. They write the audit event first and only then open
 * their own transaction, so one connection at a time is enough. Guide: §5.6 Transactions.
 */
@SpringBootTest(
        properties = {
            "spring.datasource.hikari.maximum-pool-size=1",
            // Milliseconds; the shortest wait HikariCP allows, so the trap fails fast.
            "spring.datasource.hikari.connection-timeout=250"
        })
@Import(TestcontainersConfiguration.class)
class LoanConnectionPoolIT {

    @Autowired
    private LoanService loans;

    @Autowired
    private BookRepository books;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Book book;
    private Member member;

    @BeforeEach
    void aBookAndAMember() {
        TestTables.truncateAll(jdbc);
        book = books.save(new Book("9780061054884", "The Dispossessed", 1974, 1));
        member = members.save(ada());
    }

    @Test
    void aBorrowNeedsOneConnectionAtATime() {
        LoanResponse loan = loans.borrow(book.getId(), member.getId());

        assertThat(loan.returnedAt()).isNull();
    }

    @Test
    @WithMockUser(roles = "LIBRARIAN")
    void aReturnNeedsOneConnectionAtATime() {
        LoanResponse loan = loans.borrow(book.getId(), member.getId());

        assertThat(loans.returnLoan(loan.id()).returnedAt()).isNotNull();
    }
}
