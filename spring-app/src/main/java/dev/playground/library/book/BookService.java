package dev.playground.library.book;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Book use cases; takes and returns DTOs. Missing ids and duplicate ISBNs are domain exceptions
 * ({@code NotFoundException}, {@code ConflictException}), mapped to HTTP in one place (§5.3).
 * Guide: §5.2 REST API.
 */
@Service
public class BookService {

    private final BookRepository repository;

    public BookService(BookRepository repository) {
        this.repository = repository;
    }

    public List<BookResponse> findAll() {
        return repository.findAll().stream().map(BookMapper::toResponse).toList();
    }

    public BookResponse findById(Long id) {
        return BookMapper.toResponse(getOrThrow(id));
    }

    public BookResponse create(CreateBookRequest request) {
        Book book = BookMapper.toNewBook(request);
        requireUniqueIsbn(book.getIsbn(), null);
        return BookMapper.toResponse(repository.save(book));
    }

    public BookResponse update(Long id, UpdateBookRequest request) {
        Book book = getOrThrow(id);
        // Check before apply(): the stored Book is changed in place, so a rejected update must not
        // have touched it yet.
        requireUniqueIsbn(Isbn.toIsbn13(request.isbn()), id);
        BookMapper.apply(request, book);
        return BookMapper.toResponse(repository.save(book));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Book", id);
        }
        repository.deleteById(id);
    }

    /** A business rule, so it lives in the service: another book (not this one) already has the ISBN. */
    private void requireUniqueIsbn(String isbn13, Long ownId) {
        repository
                .findByIsbn(isbn13)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new ConflictException("A book with ISBN " + isbn13 + " already exists");
                });
    }

    private Book getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Book", id));
    }
}
