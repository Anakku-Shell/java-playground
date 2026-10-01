package dev.playground.library.loan;

import static dev.playground.library.testing.TestDataFactory.ada;
import static dev.playground.library.testing.TestDataFactory.alan;
import static dev.playground.library.testing.TestDataFactory.dune;
import static dev.playground.library.testing.TestDataFactory.librarian;
import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import dev.playground.library.security.MemberUserDetails;
import dev.playground.library.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

/**
 * Whose loans a caller may see and touch: URL rules (the list is for librarians) and method
 * security (a single loan is for its member or a librarian), with real tokens.
 * Guide: §5.7 Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LoanSecurityIT {

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private BookRepository books;

    @Autowired
    private MemberRepository members;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JdbcTemplate jdbc;

    private Book dune;
    private Member ada;
    private Member alan;
    private Member librarian;

    @BeforeEach
    void aBookTwoMembersAndALibrarian() {
        TestTables.truncateAll(jdbc);
        dune = books.save(dune());
        // No password needed: the tokens come straight from TokenService, not from a login.
        ada = members.save(ada());
        alan = members.save(alan());
        librarian = members.save(librarian());
    }

    private String bearer(Member member) {
        var user = new MemberUserDetails(member.getId(), member.getEmail(), null, member.getRole());
        return "Bearer " + tokens.issue(user).accessToken();
    }

    private MvcTestResult borrow(Member caller, String body) {
        return mvc.post()
                .uri("/api/loans")
                .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .exchange();
    }

    private long loanOf(Member member) throws Exception {
        MvcTestResult borrowed = borrow(member, """
                {"bookId": %d}
                """.formatted(dune.getId()));
        assertThat(borrowed).hasStatus(HttpStatus.CREATED);
        return ((Number) JsonPath.read(borrowed.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private MvcTestResult get(Member caller, String uri, Object... variables) {
        return mvc.get()
                .uri(uri, variables)
                .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                .exchange();
    }

    @Test
    void memberCannotReadOthersLoans() throws Exception {
        long alansLoan = loanOf(alan);

        assertThat(get(alan, "/api/loans/{id}", alansLoan)).hasStatusOk();
        assertThat(get(ada, "/api/loans/{id}", alansLoan))
                .hasStatus(HttpStatus.FORBIDDEN)
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("You are not allowed to do this.");
        // The list of everyone's loans, even filtered to one member, is for librarians.
        assertThat(get(ada, "/api/loans?memberId={id}", alan.getId())).hasStatus(HttpStatus.FORBIDDEN);
        // HEAD runs the GET handler too; a rule on GET alone would let it through.
        assertThat(mvc.head()
                        .uri("/api/loans?memberId={id}", alan.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(ada)))
                .hasStatus(HttpStatus.FORBIDDEN);
        // A loan that does not exist is a 403 too, not a 404: a member cannot probe which ids exist.
        assertThat(get(ada, "/api/loans/{id}", 999)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(librarian, "/api/loans/{id}", 999)).hasStatus(HttpStatus.NOT_FOUND);
    }

    @Test
    void aMemberSeesTheirOwnLoansUnderMe() throws Exception {
        long adasLoan = loanOf(ada);
        loanOf(alan);

        assertThat(get(ada, "/api/members/me/loans"))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$[*].id")
                .asArray()
                .containsExactly((int) adasLoan);
    }

    @Test
    void aMemberCannotReturnSomeoneElsesLoan() throws Exception {
        long alansLoan = loanOf(alan);

        assertThat(mvc.post().uri("/api/loans/{id}/return", alansLoan).header(HttpHeaders.AUTHORIZATION, bearer(ada)))
                .hasStatus(HttpStatus.FORBIDDEN);

        assertThat(jdbc.queryForObject("select returned_at from loans where id = ?", Object.class, alansLoan))
                .isNull();
        // @PreAuthorize ran before the method: not even the audit event was written.
        assertThat(jdbc.queryForObject(
                        "select count(*) from audit_events where action = 'RETURN_REQUESTED'", Long.class))
                .isZero();
    }

    @Test
    void theMemberOrALibrarianCanReturnALoan() throws Exception {
        long alansLoan = loanOf(alan);
        long adasLoan = loanOf(ada);

        assertThat(mvc.post().uri("/api/loans/{id}/return", alansLoan).header(HttpHeaders.AUTHORIZATION, bearer(alan)))
                .hasStatusOk();
        assertThat(mvc.post()
                        .uri("/api/loans/{id}/return", adasLoan)
                        .header(HttpHeaders.AUTHORIZATION, bearer(librarian)))
                .hasStatusOk();
    }

    @Test
    void aMemberBorrowsForThemselvesOnly() {
        assertThat(borrow(ada, """
                        {"bookId": %d}
                        """.formatted(dune.getId())))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.memberId")
                .isEqualTo(ada.getId().intValue());

        assertThat(borrow(ada, """
                        {"bookId": %d, "memberId": %d}
                        """.formatted(dune.getId(), alan.getId()))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(jdbc.queryForObject("select count(*) from loans where member_id = ?", Long.class, alan.getId()))
                .isZero();
    }

    @Test
    void aLibrarianBorrowsOnAMembersBehalf() {
        assertThat(borrow(librarian, """
                        {"bookId": %d, "memberId": %d}
                        """.formatted(dune.getId(), alan.getId())))
                .hasStatus(HttpStatus.CREATED)
                .bodyJson()
                .extractingPath("$.memberId")
                .isEqualTo(alan.getId().intValue());
    }

    @Test
    void theAuditTrailIsForLibrarians() {
        assertThat(get(ada, "/api/audit-events")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(librarian, "/api/audit-events")).hasStatusOk();
    }
}
