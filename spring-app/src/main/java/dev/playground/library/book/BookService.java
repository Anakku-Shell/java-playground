package dev.playground.library.book;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Book use cases; takes and returns DTOs. Missing ids and duplicate ISBNs are domain exceptions
 * ({@code NotFoundException}, {@code ConflictException}), mapped to HTTP in one place (§5.3).
 * Guide: §5.2 REST API, §5.4 Persistence with JPA.
 */
@Service
public class BookService {

    private static final Sort BY_ID = Sort.by("id");

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    /** Every book, or those whose title contains {@code title} (any case). */
    public List<BookResponse> findAll(String title) {
        List<Book> books = title == null || title.isBlank()
                ? repository.findAll(BY_ID)
                : repository.findByTitleContainingIgnoreCase(title, BY_ID);
        return books.stream().map(BookMapper::toResponse).toList();
    }

    public BookResponse findById(Long id) {
        return BookMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public BookResponse create(CreateBookRequest request) {
        Book book = BookMapper.toNewBook(request);
        if (repository.existsByIsbn(book.getIsbn())) {
            throw duplicateIsbn(book.getIsbn());
        }
        return BookMapper.toResponse(repository.save(book));
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
        // Check before apply(). Before running a query, Hibernate flushes pending changes to the
        // tables that query reads (auto flush). Applying first would send the UPDATE with the
        // duplicate ISBN, and the unique constraint would fail instead of this clear 409.
        if (repository.existsByIsbnAndIdNot(isbn13, id)) {
            throw duplicateIsbn(isbn13);
        }
        BookMapper.apply(request, book);
        return BookMapper.toResponse(book);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Book", id);
        }
        repository.deleteById(id);
    }

    // A race between two requests can still pass both checks; the unique constraint then fails,
    // and GlobalExceptionHandler turns that into a 409 as well.
    private static ConflictException duplicateIsbn(String isbn13) {
        return new ConflictException("A book with ISBN " + isbn13 + " already exists");
    }

    private Book getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Book", id));
    }
}
