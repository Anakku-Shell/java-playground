package dev.playground.library.author;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Repository;

/**
 * In-memory storage. The repository is a singleton (§5.1) shared by every request thread, so the
 * map is a {@code ConcurrentHashMap} (§4.3). Only the map is thread-safe, though: the service
 * changes the stored {@code Author} objects in place, and its {@code existsById} + {@code deleteById}
 * is a check-then-act. Two concurrent requests can therefore see a half-updated author, or an
 * update can put back an author that a parallel delete just removed. That is acceptable for a
 * demo store; §5.4 replaces it with a database, and §5.6 (transactions, optimistic locking)
 * handles concurrent writers properly. Guide: §5.2 REST API.
 */
@Repository
public class AuthorRepository {

    private final Map<Long, Author> authors = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public List<Author> findAll() {
        return authors.values().stream()
                .sorted(Comparator.comparing(Author::getId))
                .toList();
    }

    public Optional<Author> findById(Long id) {
        return Optional.ofNullable(authors.get(id));
    }

    /** Inserts when the id is null (assigning a new one), replaces otherwise. */
    public Author save(Author author) {
        if (author.getId() == null) {
            author.setId(nextId.getAndIncrement());
        }
        authors.put(author.getId(), author);
        return author;
    }

    public boolean existsById(Long id) {
        return authors.containsKey(id);
    }

    public void deleteById(Long id) {
        authors.remove(id);
    }
}
