package dev.playground.core.functional;

import dev.playground.core.language.Genre;
import java.util.List;

/**
 * A fixed in-memory data set, in a fixed order, so the stream examples have exact answers. Page
 * counts are illustrative. Guide: §4.2 Collections &amp; functional.
 */
public final class BookSamples {

    private static final List<BookSample> ALL = List.of(
            new BookSample("Emma", "Jane Austen", 1815, Genre.FICTION, 474),
            new BookSample("Dune", "Frank Herbert", 1965, Genre.FICTION, 412),
            new BookSample("Dune Messiah", "Frank Herbert", 1969, Genre.FICTION, 256),
            new BookSample("The Hobbit", "J.R.R. Tolkien", 1937, Genre.FANTASY, 310),
            new BookSample("The Fellowship of the Ring", "J.R.R. Tolkien", 1954, Genre.FANTASY, 423),
            new BookSample("A Brief History of Time", "Stephen Hawking", 1988, Genre.SCIENCE, 212),
            new BookSample("Cosmos", "Carl Sagan", 1980, Genre.SCIENCE, 365),
            new BookSample("SPQR", "Mary Beard", 2015, Genre.HISTORY, 608));

    private BookSamples() {}

    /** The eight books, unmodifiable, always in the same order. */
    public static List<BookSample> all() {
        return ALL;
    }
}
