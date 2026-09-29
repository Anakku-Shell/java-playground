package dev.playground.core.language;

/**
 * A custom <b>checked</b> exception (extends Exception): callers must catch it or declare it with
 * {@code throws}. Use for recoverable conditions the caller is expected to handle.
 * Guide: §4.1 Modern language.
 */
public class BookNotAvailableException extends Exception {

    private final String isbn;

    public BookNotAvailableException(String isbn) {
        super("Book " + isbn + " is not available");
        this.isbn = isbn;
    }

    public String isbn() {
        return isbn;
    }
}
