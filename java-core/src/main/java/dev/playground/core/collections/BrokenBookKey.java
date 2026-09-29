package dev.playground.core.collections;

/**
 * Overrides {@code equals} but forgets {@code hashCode}: the classic bug. Two equal keys almost
 * certainly get different identity hash codes, and HashSet/HashMap check the hash before calling
 * {@code equals}, so a lookup with an equal key misses. Guide:
 * §4.2 Collections &amp; functional.
 */
public final class BrokenBookKey {

    private final String isbn;
    private final int edition;

    public BrokenBookKey(String isbn, int edition) {
        this.isbn = isbn;
        this.edition = edition;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BrokenBookKey that && edition == that.edition && isbn.equals(that.isbn);
    }

    // No hashCode override: that is the bug this class exists to show.

    @Override
    public String toString() {
        return "BrokenBookKey[" + isbn + ", " + edition + "]";
    }
}
