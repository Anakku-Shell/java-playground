package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Records (Java 16): transparent, immutable data carriers. The compiler writes the constructor,
 * the accessors ({@code value()}, not {@code getValue()}), equals, hashCode and toString.
 * Guide: §4.1 Modern language.
 */
class RecordsTest {

    @Test
    void compactConstructorNormalisesTheValue() {
        var isbn = new Isbn("978-0-13-468599-1");

        assertThat(isbn.value()).isEqualTo("9780134685991");
        assertThat(isbn.isIsbn13()).isTrue();
    }

    @Test
    void compactConstructorRejectsInvalidInput() {
        // Validation in the constructor means an invalid Isbn can never exist.
        assertThatThrownBy(() -> new Isbn("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 or 13 digits");
        assertThatThrownBy(() -> new Isbn(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void recordsGetValueBasedEquals() {
        var a = new Isbn("0134685997");
        var b = new Isbn("0-13-468599-7");

        // Two different objects with equal components are equal (a class would compare references).
        assertThat(a).isNotSameAs(b);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toStringListsTheComponents() {
        assertThat(new Isbn("0134685997")).hasToString("Isbn[value=0134685997]");
    }

    @Test
    void componentsAreOnlyShallowlyImmutable() {
        // The field cannot be reassigned, but the list it points to is still mutable.
        List<String> titles = new ArrayList<>(List.of("Dune"));
        var shelf = new LeakyShelf("sci-fi", titles);

        titles.add("Hyperion"); // changing the caller's list...
        shelf.titles().add("Foundation"); // ...or the one the accessor returns

        assertThat(shelf.titles()).containsExactly("Dune", "Hyperion", "Foundation");
    }

    @Test
    void defensiveCopyProtectsTheRecord() {
        List<String> titles = new ArrayList<>(List.of("Dune"));
        var shelf = new Shelf("sci-fi", titles);

        titles.add("Hyperion");

        assertThat(shelf.titles()).containsExactly("Dune");
        assertThatThrownBy(() -> shelf.titles().add("Foundation")).isInstanceOf(UnsupportedOperationException.class);
    }
}
