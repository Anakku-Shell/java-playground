package dev.playground.library.loan;

import static dev.playground.library.testing.TestDataFactory.ada;
import static dev.playground.library.testing.TestDataFactory.dune;
import static dev.playground.library.testing.TestDataFactory.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.playground.library.audit.AuditService;
import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.config.LibraryProperties;
import dev.playground.library.loan.dto.LoanResponse;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * The loan rules, with the repositories mocked and a fixed {@code Clock}: "now" is always
 * 2026-09-30T10:00Z, so the due date is an exact value. Guide: §5.5 Advanced JPA.
 */
@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");

    @Mock
    private LoanRepository loans;

    @Mock
    private BookRepository books;

    @Mock
    private MemberRepository members;

    @Mock
    private AuditService audit;

    @Mock
    private ApplicationEventPublisher events;

    private LoanService service;

    private Book dune;
    private Member ada;

    @BeforeEach
    void setUp() {
        // Built by hand: @InjectMocks only injects mocks, and the clock and settings are real values.
        var properties = new LibraryProperties("Test library", new LibraryProperties.Loans(3, 14));
        service = new LoanService(loans, books, members, properties, Clock.fixed(NOW, ZoneOffset.UTC), audit, events);

        dune = withId(dune(), 1L);
        dune.setTotalCopies(2);
        ada = withId(ada(), 7L);
    }

    private void bookAndMemberExist() {
        given(books.findWithVersionIncrementById(1L)).willReturn(Optional.of(dune));
        given(members.findWithVersionIncrementById(7L)).willReturn(Optional.of(ada));
    }

    @Test
    void borrowCreatesALoanDueInFourteenDays() {
        bookAndMemberExist();
        given(loans.save(any(Loan.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        LoanResponse loan = service.borrow(1L, 7L);

        assertThat(loan).isEqualTo(new LoanResponse(5L, 1L, "Dune", 7L, NOW, LocalDate.of(2026, 10, 14), null));
    }

    @Test
    void borrowAnnouncesTheNewLoan() {
        bookAndMemberExist();
        given(loans.save(any(Loan.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        service.borrow(1L, 7L);

        // Published inside the transaction; the listener runs after the commit (LoanEventsIT).
        then(events).should().publishEvent(new LoanCreatedEvent(5L, 1L, "Dune", 7L, LocalDate.of(2026, 10, 14)));
    }

    @Test
    void timestampsKeepOnlyWhatTheDatabaseStores() {
        // Java's clock can tick below a microsecond; timestamptz keeps microseconds. Without the
        // truncation, the POST response and a later GET would show two different loanedAt values.
        Instant precise = Instant.parse("2026-09-30T10:00:00.123456789Z");
        var properties = new LibraryProperties("Test library", new LibraryProperties.Loans(3, 14));
        service =
                new LoanService(loans, books, members, properties, Clock.fixed(precise, ZoneOffset.UTC), audit, events);
        bookAndMemberExist();
        given(loans.save(any(Loan.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        assertThat(service.borrow(1L, 7L).loanedAt()).isEqualTo(Instant.parse("2026-09-30T10:00:00.123456Z"));
    }

    @Test
    void overdueLoansAreTheActiveOnesDueBeforeToday() {
        // "Today" comes from the fixed clock: 2026-09-30 in UTC.
        Loan late = withId(new Loan(dune, ada, NOW.minus(Duration.ofDays(20)), LocalDate.of(2026, 9, 20)), 5L);
        given(loans.findOverdue(LocalDate.of(2026, 9, 30))).willReturn(List.of(late));

        assertThat(service.findOverdue())
                .containsExactly(new LoanResponse(
                        5L, 1L, "Dune", 7L, Instant.parse("2026-09-10T10:00:00Z"), LocalDate.of(2026, 9, 20), null));
    }

    @Test
    void theLastCopyAndTheLastAllowedLoanCanBeBorrowed() {
        bookAndMemberExist();
        given(loans.countByBookIdAndReturnedAtIsNull(1L)).willReturn(1L); // 2 copies, 1 out
        given(loans.countByMemberIdAndReturnedAtIsNull(7L)).willReturn(2L); // limit 3
        given(loans.save(any(Loan.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        assertThat(service.borrow(1L, 7L).id()).isEqualTo(5L);
    }

    @Test
    void noCopyLeftIsAConflict() {
        bookAndMemberExist();
        given(loans.countByBookIdAndReturnedAtIsNull(1L)).willReturn(2L); // both copies are out

        assertThatThrownBy(() -> service.borrow(1L, 7L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Book 1 has no available copies");
        then(loans).should(never()).save(any());
        then(events).shouldHaveNoInteractions();
    }

    @Test
    void aMemberAtTheLimitCannotBorrowMore() {
        bookAndMemberExist();
        given(loans.countByMemberIdAndReturnedAtIsNull(7L)).willReturn(3L); // library.loans.max-active

        assertThatThrownBy(() -> service.borrow(1L, 7L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Member 7 already has 3 active loans");
        then(loans).should(never()).save(any());
    }

    @Test
    void unknownBookOrMemberIsNotFound() {
        given(books.findWithVersionIncrementById(1L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.borrow(1L, 7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book 1 not found");

        given(books.findWithVersionIncrementById(1L)).willReturn(Optional.of(dune));
        given(members.findWithVersionIncrementById(7L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.borrow(1L, 7L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Member 7 not found");
    }

    @Test
    void returnStampsTheCurrentTime() {
        Loan loan = withId(new Loan(dune, ada, NOW.minusSeconds(86_400), LocalDate.of(2026, 10, 13)), 5L);
        given(loans.findById(5L)).willReturn(Optional.of(loan));

        LoanResponse returned = service.returnLoan(5L);

        assertThat(returned.returnedAt()).isEqualTo(NOW);
        assertThat(loan.isActive()).isFalse(); // the managed entity changed: dirty checking saves it
    }

    @Test
    void returningTwiceIsRejected() {
        Instant firstReturn = NOW.minusSeconds(3_600);
        Loan loan = withId(new Loan(dune, ada, NOW.minusSeconds(86_400), LocalDate.of(2026, 10, 13)), 5L);
        loan.markReturned(firstReturn);
        given(loans.findById(5L)).willReturn(Optional.of(loan));

        assertThatThrownBy(() -> service.returnLoan(5L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Loan 5 was already returned");
        assertThat(loan.getReturnedAt()).isEqualTo(firstReturn); // untouched
    }

    @Test
    void requestsAreAuditedEvenWhenTheyFail() {
        // The audit call comes before any check, so a rejected request leaves an event too. It runs
        // in a transaction of its own; LoanControllerIT shows the event survives the rollback.
        given(books.findWithVersionIncrementById(1L)).willReturn(Optional.empty());
        given(loans.findById(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.borrow(1L, 7L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.returnLoan(5L)).isInstanceOf(NotFoundException.class);

        then(audit).should().record("BORROW_REQUESTED", "book 1, member 7");
        then(audit).should().record("RETURN_REQUESTED", "loan 5");
    }

    @Test
    void unknownLoanIsNotFound() {
        given(loans.findById(5L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.returnLoan(5L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Loan 5 not found");
    }
}
