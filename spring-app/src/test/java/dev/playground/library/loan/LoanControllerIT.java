package dev.playground.library.loan;

import static dev.playground.library.testing.TestDataFactory.ada;
import static dev.playground.library.testing.TestDataFactory.alan;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Borrowing and returning from HTTP to PostgreSQL: the loan rules with real counts, and how a loan
 * changes a book's {@code availableCopies}. Guide: §5.5 Advanced JPA.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
// Security is not the subject here (LoanSecurityIT is): a librarian, who may borrow on any member's
// behalf. The name is the member id CurrentMember reads; 999 is no member.
@WithMockUser(username = "999", roles = "LIBRARIAN")
class LoanControllerIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private BookRepository books;

    @Autowired
    private MemberRepository members;

    @Autowired
    private JdbcTemplate jdbc;

    private Book dune;
    private Member ada;
    private Member alan;

    @BeforeEach
    void aBookAndTwoMembers() {
        TestTables.truncateAll(jdbc);
        dune = books.save(new Book("9780441013593", "Dune", 1965, 2));
        ada = members.save(ada());
        alan = members.save(alan());
    }

    private MvcTestResult borrow(Book book, Member member) {
        return mvc.post()
                .uri("/api/loans")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"bookId": %d, "memberId": %d}
                        """.formatted(book.getId(), member.getId()))
                .exchange();
    }

    private static long idOf(MvcTestResult result) throws UnsupportedEncodingException {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    void borrowListReturnAndReturnAgain() throws Exception {
        MvcTestResult borrowed = borrow(dune, ada);
        assertThat(borrowed).hasStatus(HttpStatus.CREATED);
        assertThat(borrowed).bodyJson().isLenientlyEqualTo("""
                        {"bookId": %d, "bookTitle": "Dune", "memberId": %d, "returnedAt": null}
                        """.formatted(dune.getId(), ada.getId()));
        // Instant and LocalDate as ISO-8601 strings.
        assertThat(borrowed).bodyJson().extractingPath("$.loanedAt").asString().matches("\\d{4}-\\d{2}-\\d{2}T.+Z");
        assertThat(borrowed).bodyJson().extractingPath("$.dueDate").asString().matches("\\d{4}-\\d{2}-\\d{2}");
        long loan = idOf(borrowed);
        assertThat(mvc.get().uri(borrowed.getResponse().getHeader("Location"))).hasStatusOk();

        // One of the two copies is out.
        assertThat(mvc.get().uri("/api/books/{id}", dune.getId()))
                .bodyJson()
                .extractingPath("$.availableCopies")
                .isEqualTo(1);
        assertThat(mvc.get()
                        .uri("/api/loans")
                        .queryParam("memberId", String.valueOf(ada.getId()))
                        .queryParam("active", "true"))
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly((int) loan);

        assertThat(mvc.post().uri("/api/loans/{id}/return", loan))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.returnedAt")
                .isNotNull();
        assertThat(mvc.post().uri("/api/loans/{id}/return", loan))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Loan " + loan + " was already returned");

        assertThat(mvc.get().uri("/api/loans").queryParam("active", "false"))
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly((int) loan);
        assertThat(mvc.get().uri("/api/books/{id}", dune.getId()))
                .bodyJson()
                .extractingPath("$.availableCopies")
                .isEqualTo(2);
    }

    @Test
    void noCopyLeftIs409() {
        borrow(dune, ada);
        borrow(dune, alan);

        assertThat(borrow(dune, ada))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Book " + dune.getId() + " has no available copies");
        assertThat(jdbc.queryForObject("select count(*) from loans", Long.class))
                .isEqualTo(2);
    }

    @Test
    void aRejectedBorrowIsStillAudited() {
        Book noCopies = books.save(new Book("9780061054884", "The Dispossessed", 1974, 0));

        assertThat(borrow(noCopies, ada)).hasStatus(HttpStatus.CONFLICT);

        // The borrow's transaction rolled back, so no loan. The audit event was written in a
        // transaction of its own (REQUIRES_NEW), which had committed before the 409 was thrown.
        assertThat(jdbc.queryForObject("select count(*) from loans", Long.class))
                .isZero();
        assertThat(mvc.get().uri("/api/audit-events")).bodyJson().isLenientlyEqualTo("""
                [{"action": "BORROW_REQUESTED", "detail": "book %d, member %d"}]
                """.formatted(
                        noCopies.getId(), ada.getId()));
    }

    @Test
    void aMemberCannotHoldMoreThanTheMaximum() {
        // library.loans.max-active defaults to 3.
        for (String isbn : new String[] {"9780593098233", "9780141439587", "9780345539434"}) {
            assertThat(borrow(books.save(new Book(isbn, "Book " + isbn, 2000, 1)), ada))
                    .hasStatus(HttpStatus.CREATED);
        }

        assertThat(borrow(dune, ada))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Member " + ada.getId() + " already has 3 active loans");
    }

    @Test
    void theRulesThatLoansAdd() {
        borrow(dune, ada);

        // The loan keeps its history: the book cannot go.
        assertThat(mvc.delete().uri("/api/books/{id}", dune.getId()))
                .hasStatus(HttpStatus.CONFLICT)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("Book " + dune.getId() + " has loans and cannot be deleted");

        // One copy is out: the book cannot shrink to zero copies.
        assertThat(mvc.put()
                        .uri("/api/books/{id}", dune.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780441013593", "title": "Dune", "totalCopies": 0}
                                """))
                .hasStatus(HttpStatus.CONFLICT);
    }

    @Test
    void unknownBookOrMemberIs404() {
        assertThat(mvc.post()
                        .uri("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bookId": 999, "memberId": %d}
                                """.formatted(ada.getId())))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(mvc.post()
                        .uri("/api/loans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .hasStatus(HttpStatus.BAD_REQUEST);
    }
}
