package dev.playground.core.collections;

import java.util.Objects;

/**
 * equals and hashCode are right, but a field they use can change. Mutating it while the object sits
 * in a hash collection strands it in the wrong bucket. Guide: §4.2 Collections &amp; functional.
 */
public final class MutableBookKey {

    private final String isbn;
    private int edition;

    public MutableBookKey(String isbn, int edition) {
        this.isbn = Objects.requireNonNull(isbn, "isbn");
        this.edition = edition;
    }

    public void setEdition(int edition) {
        this.edition = edition;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MutableBookKey that && edition == that.edition && isbn.equals(that.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(isbn, edition);
    }

    @Override
    public String toString() {
        return "MutableBookKey[" + isbn + ", " + edition + "]";
    }
}
