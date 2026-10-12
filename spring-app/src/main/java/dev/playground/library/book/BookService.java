package dev.playground.library.book;

import static java.util.stream.Collectors.toMap;

import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.common.PageResponse;
import dev.playground.library.common.Paging;
import dev.playground.library.loan.ActiveLoanCount;
import dev.playground.library.loan.LoanRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Book use cases; takes and returns DTOs. Missing ids and broken rules are domain exceptions
 * ({@code NotFoundException}, {@code ConflictException}), mapped to HTTP in one place (§5.3).
 * {@code availableCopies} is computed from the active loans on every read.
 *
 * <p>{@code @Transactional(readOnly = true)} on the class gives every public method a read-only
 * transaction: Hibernate skips the flush (no dirty checking) and PostgreSQL refuses writes. The
 * write methods override it with their own {@code @Transactional}. Guide: §5.2 REST API, §5.4
 * Persistence with JPA, §5.5 Advanced JPA, §5.6 Transactions.
 */
@Service
@Transactional(readOnly = true)
public class BookService {

    /** What ?sort= may name: plain columns only (Paging.sanitize explains why). */
    private static final Set<String> SORTABLE = Set.of("id", "isbn", "title", "publishedYear", "totalCopies");

    private final BookRepository books;
    private final AuthorRepository authors;
    private final LoanRepository loans;

    public BookService(BookRepository books, AuthorRepository authors, LoanRepository loans) {
        this.books = books;
        this.authors = authors;
        this.loans = loans;
    }

    /**
     * One page of books, optionally filtered by title and author. A constant number of statements
     * whatever the page size: the page, a count(*) when the page is not the whole result, the authors
     * of all its books (one batch, see
     * {@code Book.authors}), and one grouped loan count. The class-level read-only transaction keeps the
     * persistence context open while the mapping loads the lazy authors (open-in-view is off).
     */
    public PageResponse<BookResponse> findAll(String title, Long authorId, Pageable pageable) {
        Specification<Book> filters =
                Specification.allOf(BookSpecifications.titleContains(title), BookSpecifications.hasAuthor(authorId));
        Page<Book> page = books.findAll(filters, Paging.sanitize(pageable, SORTABLE));
        Map<Long, Long> onLoan = activeLoansByBook(page.getContent());
        return PageResponse.from(page.map(book -> BookMapper.toResponse(book, onLoan.getOrDefault(book.getId(), 0L))));
    }

    public BookResponse findById(Long id) {
        // The entity graph brings the authors in the same query.
        Book book = books.findWithAuthorsById(id).orElseThrow(() -> new NotFoundException("Book", id));
        return BookMapper.toResponse(book, loans.countByBookIdAndReturnedAtIsNull(id));
    }

    @Transactional
    public BookResponse create(CreateBookRequest request) {
        Book book = BookMapper.toNewBook(request);
        if (books.existsByIsbn(book.getIsbn())) {
            throw duplicateIsbn(book.getIsbn());
        }
        book.replaceAuthors(findAuthors(request.authorIds()));
        return BookMapper.toResponse(books.save(book), 0);
    }

    /**
     * {@code @Transactional}: the book loaded here stays <em>managed</em> until the method returns,
     * and Hibernate writes whatever changed on commit (dirty checking), so there is no
     * {@code save()} call. §5.6 covers transactions in full.
     */
    @Transactional
    public BookResponse update(Long id, UpdateBookRequest request) {
        Book book = getOrThrow(id);
        String isbn13 = Isbn.toIsbn13(request.isbn());
        // Every query runs before apply(). Before a query, Hibernate flushes pending changes to the
        // tables it reads (auto flush). Applying first would send the UPDATE with a duplicate ISBN,
        // and the unique constraint would fail instead of this clear 409.
        if (books.existsByIsbnAndIdNot(isbn13, id)) {
            throw duplicateIsbn(isbn13);
        }
        Set<Author> newAuthors = findAuthors(request.authorIds());
        long onLoan = loans.countByBookIdAndReturnedAtIsNull(id);
        if (request.totalCopies() < onLoan) {
            throw new ConflictException(
                    "Book " + id + " has " + onLoan + " copies on loan; totalCopies cannot be lower");
        }
        BookMapper.apply(request, book);
        book.replaceAuthors(newAuthors);
        return BookMapper.toResponse(book, onLoan);
    }

    @Transactional
    public void delete(Long id) {
        if (!books.existsById(id)) {
            throw new NotFoundException("Book", id);
        }
        if (loans.existsByBookId(id)) {
            throw new ConflictException("Book " + id + " has loans and cannot be deleted");
        }
        // Hibernate deletes the book's book_authors rows first (it owns them), then the book.
        books.deleteById(id);
    }

    /** The authors with these ids; an id that does not exist is a 404, like a missing book. */
    private Set<Author> findAuthors(Set<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        List<Author> found = authors.findAllById(ids);
        if (found.size() < ids.size()) {
            Set<Long> foundIds = new HashSet<>(found.stream().map(Author::getId).toList());
            Long missing = ids.stream()
                    .filter(id -> !foundIds.contains(id))
                    .sorted()
                    .findFirst()
                    .orElseThrow();
            throw new NotFoundException("Author", missing);
        }
        return new HashSet<>(found);
    }

    /** Active loans per book id, in one query; books without active loans are absent. */
    private Map<Long, Long> activeLoansByBook(List<Book> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        return loans.countActiveByBookIds(page.stream().map(Book::getId).toList()).stream()
                .collect(toMap(ActiveLoanCount::bookId, ActiveLoanCount::activeLoans));
    }

    // A race between two requests can still pass both checks; the unique constraint then fails,
    // and GlobalExceptionHandler turns that into a 409 as well.
    private static ConflictException duplicateIsbn(String isbn13) {
        return new ConflictException("A book with ISBN " + isbn13 + " already exists");
    }

    private Book getOrThrow(Long id) {
        return books.findById(id).orElseThrow(() -> new NotFoundException("Book", id));
    }
}
