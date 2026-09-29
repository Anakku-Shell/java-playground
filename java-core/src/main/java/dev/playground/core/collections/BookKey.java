package dev.playground.core.collections;

import java.util.Comparator;
import java.util.Objects;

/**
 * A correct hand-written value class: immutable, {@code equals} and {@code hashCode} over the same
 * fields, and a natural order through {@link Comparable}. A record would generate the first two for
 * you; this class shows what that saves. Guide: §4.2 Collections &amp; functional.
 */
public final class BookKey implements Comparable<BookKey> {

    // Built once: comparing chains are cheap to call but not free to build.
    private static final Comparator<BookKey> NATURAL_ORDER =
            Comparator.comparing(BookKey::isbn).thenComparingInt(BookKey::edition);

    private final String isbn;
    private final int edition;

    public BookKey(String isbn, int edition) {
        this.isbn = Objects.requireNonNull(isbn, "isbn");
        this.edition = edition;
    }

    public String isbn() {
        return isbn;
    }

    public int edition() {
        return edition;
    }

    @Override
    public boolean equals(Object other) {
        // The pattern also rejects null and other types; a final class needs no getClass() check.
        return other instanceof BookKey that && edition == that.edition && isbn.equals(that.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isbn, edition); // the same fields as equals
    }

    /** Keep compareTo consistent with equals: 0 exactly when the keys are equal. */
    @Override
    public int compareTo(BookKey other) {
        return NATURAL_ORDER.compare(this, other);
    }

    @Override
    public String toString() {
        return "BookKey[" + isbn + ", " + edition + "]";
    }
}
