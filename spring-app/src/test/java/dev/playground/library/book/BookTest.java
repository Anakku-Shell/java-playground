package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Entity identity: equal when the database id is equal, and a stable hash code while the id is
 * being assigned. The id has no setter (the database generates it), so the test sets the field the
 * way Hibernate does, by reflection. Guide: §5.4 Persistence with JPA.
 */
class BookTest {

    private static Book withId(Long id, String title) {
        Book book = new Book("9780441013593", title, 1965, 3);
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    @Test
    void sameIdMeansSameBook() {
        // Two loads of the same row (say, in two persistence contexts) are two Java objects.
        assertThat(withId(7L, "Dune")).isEqualTo(withId(7L, "Dune (edited)"));
        assertThat(withId(7L, "Dune")).isNotEqualTo(withId(8L, "Dune"));
    }

    @Test
    void newBooksAreOnlyEqualToThemselves() {
        Book first = new Book("9780441013593", "Dune", 1965, 3);
        Book second = new Book("9780441013593", "Dune", 1965, 3);

        // No id yet: nothing says they are the same row, even with the same fields.
        assertThat(first).isEqualTo(first).isNotEqualTo(second);
    }

    @Test
    void staysInAHashSetWhenTheIdIsAssigned() {
        Book book = new Book("9780441013593", "Dune", 1965, 3);
        Set<Book> books = new HashSet<>(Set.of(book));

        ReflectionTestUtils.setField(book, "id", 7L); // what persist() does (IDENTITY: at once)

        // With an id-based hashCode the book would now sit in the wrong bucket and be "lost".
        assertThat(books).contains(book);
    }
}
