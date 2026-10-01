package dev.playground.library.loan;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borrowing and returning. The rules: a copy must be available (total copies minus active loans),
 * a member holds at most {@code library.loans.max-active} loans, a loan is due
 * {@code library.loans.duration-days} after today, and a loan is returned once. Each broken rule is
 * a {@code ConflictException} (409). Reads run in the class-level read-only transaction; borrow and
 * return are read-write, and their races are closed with optimistic locking. Guide: §5.5 Advanced
 * JPA, §5.6 Transactions, §5.7 Security (who may read and return a loan), §5.9 Beyond CRUD (the
 * event a borrow publishes, the overdue loans).
 */
@Service
@Transactional(readOnly = true)
public class LoanService {

    private final LoanRepository loans;
    private final BookRepository books;
    private final MemberRepository members;
    private final LibraryProperties.Loans rules;
    private final Clock clock;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public LoanService(
            LoanRepository loans,
            BookRepository books,
            MemberRepository members,
            LibraryProperties properties,
            Clock clock,
            AuditService audit,
            ApplicationEventPublisher events) {
        this.loans = loans;
        this.books = books;
        this.members = members;
        this.rules = properties.loans();
        this.clock = clock;
        this.audit = audit;
        this.events = events;
    }

    /**
     * Two borrows can both pass a check before either inserts: the checks read committed data only,
     * and neither loan is committed yet. Two borrows of the last copy, or two borrows by a member one
     * loan short of the maximum. So each rule gets a row to collide on: the book (copies) and the
     * member (max-active) are read with a version bump. At commit, the second of two overlapping
     * borrows finds a newer version, fails with an optimistic locking exception (409, retry) and its
     * loan is rolled back. {@code LoanConcurrencyIT} forces these races.
     */
    @Transactional
    public LoanResponse borrow(Long bookId, Long memberId) {
        // First, and in a transaction of its own (see AuditService): a rejected borrow is audited too.
        // The cost: this transaction already holds a connection, and the audit takes a second one.
        audit.record("BORROW_REQUESTED", "book " + bookId + ", member " + memberId);
        // Both read with a version bump: see the methods, and the races described above.
        Book book = books.findWithVersionIncrementById(bookId).orElseThrow(() -> new NotFoundException("Book", bookId));
        Member member = members.findWithVersionIncrementById(memberId)
                .orElseThrow(() -> new NotFoundException("Member", memberId));

        if (loans.countByMemberIdAndReturnedAtIsNull(member.getId()) >= rules.maxActive()) {
            throw new ConflictException(
                    "Member " + member.getId() + " already has " + rules.maxActive() + " active loans");
        }
        if (loans.countByBookIdAndReturnedAtIsNull(book.getId()) >= book.getTotalCopies()) {
            throw new ConflictException("Book " + book.getId() + " has no available copies");
        }

        // The clock's zone decides which day "today" is.
        LocalDate dueDate = LocalDate.now(clock).plusDays(rules.durationDays());
        Loan loan = loans.save(new Loan(book, member, now(), dueDate));
        // Delivered to @TransactionalEventListener methods only if this transaction commits (§5.9).
        events.publishEvent(new LoanCreatedEvent(loan.getId(), book.getId(), book.getTitle(), member.getId(), dueDate));
        return LoanMapper.toResponse(loan);
    }

    /**
     * Same race as borrow: two returns at once both see an active loan. {@code Loan.version} makes
     * the second UPDATE match no row, so it fails (409) and the first {@code returnedAt} stays.
     *
     * <p>Only the loan's member or a librarian (§5.7). The check runs before the method, and before
     * its transaction: a refused return is not even audited.
     */
    @PreAuthorize("hasRole('LIBRARIAN') or @loanAccess.isOwner(#id, authentication)")
    @Transactional
    public LoanResponse returnLoan(Long id) {
        audit.record("RETURN_REQUESTED", "loan " + id);
        Loan loan = getOrThrow(id);
        if (!loan.isActive()) {
            throw new ConflictException("Loan " + id + " was already returned");
        }
        loan.markReturned(now()); // dirty checking writes it on commit
        return LoanMapper.toResponse(loan);
    }

    /**
     * One loan, for its member or a librarian (§5.7), checked like {@code returnLoan}. Not with
     * {@code @PostAuthorize} on the returned loan: a missing loan would then be a 404 and someone
     * else's a 403, which tells a member which loan ids exist.
     */
    @PreAuthorize("hasRole('LIBRARIAN') or @loanAccess.isOwner(#id, authentication)")
    public LoanResponse findById(Long id) {
        return LoanMapper.toResponse(getOrThrow(id));
    }

    /** Loans filtered by member and/or state; each filter is optional. Books come in the same query. */
    public List<LoanResponse> findAll(Long memberId, Boolean active) {
        Specification<Loan> filters =
                Specification.allOf(LoanSpecifications.ofMember(memberId), LoanSpecifications.active(active));
        return loans.findAll(filters, Sort.by("id")).stream()
                .map(LoanMapper::toResponse)
                .toList();
    }

    /**
     * Active loans past their due date, oldest first, for librarians and {@code OverdueLoanJob}.
     * "Today" is the clock's date, like the due date of a borrow. No {@code @PreAuthorize}, unlike
     * {@code findById}: the job calls it with no user at all, so the URL rule on
     * {@code /api/loans/overdue} is its only guard (§5.7). Guide: §5.9 Beyond CRUD.
     */
    public List<LoanResponse> findOverdue() {
        return loans.findOverdue(LocalDate.now(clock)).stream()
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
