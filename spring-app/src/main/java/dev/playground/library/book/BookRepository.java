package dev.playground.library.book;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * In-memory storage until §5.4. Same shape, and the same concurrency caveats, as
 * {@code AuthorRepository}. Guide: §5.2 REST API.
 */
@Repository
public class BookRepository {

    private final Map<Long, Book> books = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public List<Book> findAll() {
        return books.values().stream().sorted(Comparator.comparing(Book::getId)).toList();
    }

    public Optional<Book> findById(Long id) {
        return Optional.ofNullable(books.get(id));
    }

    /** Inserts when the id is null (assigning a new one), replaces otherwise. */
    public Book save(Book book) {
        if (book.getId() == null) {
            book.setId(nextId.getAndIncrement());
        }
        books.put(book.getId(), book);
        return book;
    }

    public boolean existsById(Long id) {
        return books.containsKey(id);
    }

    public void deleteById(Long id) {
        books.remove(id);
    }
}
