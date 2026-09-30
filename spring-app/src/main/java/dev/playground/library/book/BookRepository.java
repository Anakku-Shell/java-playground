package dev.playground.library.book;

import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * A Spring Data repository: only an interface. At startup Spring Data generates the
 * implementation. {@code JpaRepository} brings {@code findAll}, {@code findById}, {@code save},
 * {@code deleteById}... and every other method below becomes a query, in one of three ways.
 * Guide: §5.4 Persistence with JPA.
 */
public interface BookRepository extends JpaRepository<Book, Long> {

    // 1. Derived queries: the query is parsed from the method name. ISBNs are in the canonical
    // 13-digit form (Isbn.toIsbn13).
    boolean existsByIsbn(String isbn);

    /** Does a book other than {@code id} have this ISBN? For updates. */
    boolean existsByIsbnAndIdNot(String isbn, Long id);

    List<Book> findByTitleContainingIgnoreCase(String title, Sort sort);

    // 2. JPQL: queries on entities and fields (Book, publishedYear), not tables and columns.
    // :from and :to bind to the parameters by name (compiled with -parameters).
    @Query("select b from Book b where b.publishedYear between :from and :to order by b.publishedYear, b.title")
    List<Book> findPublishedBetween(int from, int to);

    // 3. Native SQL, for what JPQL cannot express: here PostgreSQL full-text search, which matches
    // word stems ("dunes" finds "Dune"). Tied to PostgreSQL, and not checked at startup.
    @Query(
            value = "select * from books where to_tsvector('english', title) @@ plainto_tsquery('english', :words)"
                    + " order by id",
            nativeQuery = true)
    List<Book> searchTitles(String words);
}
