package dev.playground.library.book;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Book use cases; takes and returns DTOs. Guide: §5.2 REST API. */
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
        return BookMapper.toResponse(repository.save(BookMapper.toNewBook(request)));
    }

    public BookResponse update(Long id, UpdateBookRequest request) {
        Book book = getOrThrow(id);
        BookMapper.apply(request, book);
        return BookMapper.toResponse(repository.save(book));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw notFound(id);
        }
        repository.deleteById(id);
    }

    private Book getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    // Temporary until §5.3 (see AuthorService).
    private static ResponseStatusException notFound(Long id) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Book " + id + " not found");
    }
}
