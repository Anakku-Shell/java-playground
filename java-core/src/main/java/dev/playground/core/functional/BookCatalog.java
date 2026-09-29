package dev.playground.core.functional;

import java.util.List;
import java.util.Optional;

/**
 * Lookups that may find nothing, so they return {@link Optional} instead of null. Spring Data
 * repositories do the same ({@code findById} returns {@code Optional<T>}). Guide: §4.2 Collections
 * &amp; functional.
 */
public final class BookCatalog {

    private final List<BookSample> books;

    public BookCatalog(List<BookSample> books) {
        this.books = List.copyOf(books);
    }

    public Optional<BookSample> findByTitle(String title) {
        return books.stream().filter(b -> b.title().equals(title)).findFirst();
    }

    public Optional<BookSample> findFirstByAuthor(String author) {
        return books.stream().filter(b -> b.author().equals(author)).findFirst();
    }
}
