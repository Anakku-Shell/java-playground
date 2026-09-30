package dev.playground.library.loan;

import dev.playground.library.book.Book;
import dev.playground.library.book.BookRepository;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.config.LibraryProperties;
import dev.playground.library.loan.dto.CreateLoanRequest;
import dev.playground.library.loan.dto.LoanResponse;
import dev.playground.library.member.Member;
import dev.playground.library.member.MemberRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrowing and returning. The rules: a copy must be available (total copies minus active loans),
 * a member holds at most {@code library.loans.max-active} loans, a loan is due
 * {@code library.loans.duration-days} after today, and a loan is returned once. Each broken rule is
 * a {@code ConflictException} (409). Guide: §5.5 Advanced JPA.
 */
@Service
public class LoanService {

    private final LoanRepository loans;
    private final BookRepository books;
    private final MemberRepository members;
    private final LibraryProperties.Loans rules;
    private final Clock clock;

    public LoanService(
            LoanRepository loans,
            BookRepository books,
            MemberRepository members,
            LibraryProperties properties,
            Clock clock) {
        this.loans = loans;
        this.books = books;
        this.members = members;
        this.rules = properties.loans();
        this.clock = clock;
    }

    /**
     * Two requests for the last copy can both pass the availability check before either inserts:
     * both succeed. §5.6 closes that race with optimistic locking.
     */
    @Transactional
    public LoanResponse borrow(CreateLoanRequest request) {
        Book book = books.findById(request.bookId()).orElseThrow(() -> new NotFoundException("Book", request.bookId()));
        Member member = members.findById(request.memberId())
                .orElseThrow(() -> new NotFoundException("Member", request.memberId()));

        if (loans.countByMemberIdAndReturnedAtIsNull(member.getId()) >= rules.maxActive()) {
            throw new ConflictException(
                    "Member " + member.getId() + " already has " + rules.maxActive() + " active loans");
        }
        if (loans.countByBookIdAndReturnedAtIsNull(book.getId()) >= book.getTotalCopies()) {
            throw new ConflictException("Book " + book.getId() + " has no available copies");
        }

        // The clock's zone decides which day "today" is.
        LocalDate dueDate = LocalDate.now(clock).plusDays(rules.durationDays());
        return LoanMapper.toResponse(loans.save(new Loan(book, member, now(), dueDate)));
    }

    /** Same race as borrow: two returns at once both pass the check; §5.6 deals with it. */
    @Transactional
    public LoanResponse returnLoan(Long id) {
        Loan loan = getOrThrow(id);
        if (!loan.isActive()) {
            throw new ConflictException("Loan " + id + " was already returned");
        }
        loan.markReturned(now()); // dirty checking writes it on commit
        return LoanMapper.toResponse(loan);
    }

    // readOnly: the mapping touches the lazy book, which needs an open persistence context (open-in-
    // view is off). readOnly also tells Hibernate to skip dirty checking. §5.6 covers it in full.
    @Transactional(readOnly = true)
    public LoanResponse findById(Long id) {
        return LoanMapper.toResponse(getOrThrow(id));
    }

    /** Loans filtered by member and/or state; each filter is optional. Books come in the same query. */
    @Transactional(readOnly = true)
    public List<LoanResponse> findAll(Long memberId, Boolean active) {
        Specification<Loan> filters =
                Specification.allOf(LoanSpecifications.ofMember(memberId), LoanSpecifications.active(active));
        return loans.findAll(filters, Sort.by("id")).stream()
                .map(LoanMapper::toResponse)
                .toList();
    }

    /**
     * The current time at the precision timestamptz stores (microseconds). Java's clock can be finer,
     * and PostgreSQL would round the rest away: the response to POST would then show a loanedAt
     * that a later GET does not.
     */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private Loan getOrThrow(Long id) {
        return loans.findById(id).orElseThrow(() -> new NotFoundException("Loan", id));
    }
}
