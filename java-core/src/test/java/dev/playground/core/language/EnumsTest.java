package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Java enums are full classes: each constant is a singleton instance that can carry fields and
 * methods (a C# enum is just a named integer). Guide: §4.1 Modern language.
 */
class EnumsTest {

    @Test
    void constantsCarryFieldsAndMethods() {
        assertThat(Genre.FANTASY.label()).isEqualTo("Fantasy");
        assertThat(Genre.FANTASY.isFiction()).isTrue();
        assertThat(Genre.fromLabel("History")).isEqualTo(Genre.HISTORY);
        assertThatThrownBy(() -> Genre.fromLabel("Poetry")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void valuesFollowDeclarationOrder() {
        assertThat(Genre.values()).containsExactly(Genre.FICTION, Genre.SCIENCE, Genre.HISTORY, Genre.FANTASY);
        // ordinal() is the position. Do not persist it: reordering the constants changes it.
        assertThat(Genre.SCIENCE.ordinal()).isEqualTo(1);
    }

    @Test
    void valueOfMatchesTheConstantNameExactly() {
        assertThat(Genre.valueOf("SCIENCE")).isEqualTo(Genre.SCIENCE);
        // Case-sensitive, and it throws instead of returning null.
        assertThatThrownBy(() -> Genre.valueOf("Science")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void constantsAreSingletons() {
        // There is exactly one FICTION object in the JVM, so == is safe for enums, unlike most object types.
        Genre fromName = Genre.valueOf("FICTION");

        assertThat(fromName == Genre.FICTION).isTrue();
    }

    @Test
    void enumMapIsKeyedAndOrderedByTheEnum() {
        // EnumMap: an array indexed by ordinal under the hood. Fast, and iterates in declaration order.
        Map<Genre, Integer> copies = new EnumMap<>(Genre.class);
        for (Genre genre : List.of(Genre.HISTORY, Genre.FICTION, Genre.HISTORY)) {
            copies.merge(genre, 1, Integer::sum);
        }

        assertThat(copies).containsExactly(Map.entry(Genre.FICTION, 1), Map.entry(Genre.HISTORY, 2));
    }

    @Test
    void switchExpressionOverAnEnumNeedsNoDefault() {
        // A switch *expression* must be exhaustive. Listing every constant is enough, so adding a
        // new Genre later turns floorOf into a compile error. An old-style switch statement would
        // just skip the new constant silently.
        assertThat(floorOf(Genre.FANTASY)).isEqualTo(1);
        assertThat(floorOf(Genre.SCIENCE)).isEqualTo(2);
        assertThat(floorOf(Genre.HISTORY)).isEqualTo(3);
    }

    private static int floorOf(Genre genre) {
        return switch (genre) {
            case FICTION, FANTASY -> 1; // arrow cases never fall through; no break needed
            case SCIENCE -> 2;
            case HISTORY -> 3;
        };
    }
}
