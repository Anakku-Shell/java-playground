package dev.playground.core.collections;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The equals/hashCode contract and ordering with Comparable and Comparator. Guide: §4.2
 * Collections &amp; functional.
 *
 * <p>Contract: objects that are {@code equals} must return the same {@code hashCode}. Hash-based
 * collections compare the stored hash first and only then call {@code equals}, so breaking the
 * contract makes elements "disappear". Records generate both correctly.
 */
class EqualsHashCodeTest {

    @Test
    void equalsWithoutHashCodeLosesTheKeyInAHashSet() {
        Set<BrokenBookKey> keys = new HashSet<>();
        keys.add(new BrokenBookKey("0134685997", 3));

        var sameValue = new BrokenBookKey("0134685997", 3);

        assertThat(sameValue).isEqualTo(new BrokenBookKey("0134685997", 3)); // equals says yes...
        // ...but each object keeps its own identity hash code, and HashSet compares hashes before it
        // calls equals, so the lookup misses. (It would only find it if both identity hashes happened
        // to be equal, which is vanishingly unlikely.)
        assertThat(keys.contains(sameValue)).isFalse();
    }

    @Test
    void equalsAndHashCodeTogetherWork() {
        Set<BookKey> keys = new HashSet<>();
        keys.add(new BookKey("0134685997", 3));
        keys.add(new BookKey("0134685997", 3)); // a duplicate: ignored

        assertThat(keys).hasSize(1);
        assertThat(keys.contains(new BookKey("0134685997", 3))).isTrue();

        Map<BookKey, Integer> copies = new HashMap<>();
        copies.put(new BookKey("0134685997", 3), 2);
        assertThat(copies.get(new BookKey("0134685997", 3))).isEqualTo(2);
    }

    @Test
    void mutatingAKeyInsideAHashSetLosesIt() {
        var key = new MutableBookKey("0134685997", 3);
        Set<MutableBookKey> keys = new HashSet<>(List.of(key));

        key.setEdition(4); // the hash changes, but the set filed it under the old one

        assertThat(keys.contains(key)).isFalse();
        assertThat(keys).hasSize(1); // still in there, now unreachable by lookup
        // Lesson: keys of hash collections should be immutable (records, String, BookKey).
    }

    @Test
    void comparableGivesTheNaturalOrder() {
        // TreeSet uses compareTo (not equals/hashCode) to sort and to detect duplicates.
        Set<BookKey> sorted = new TreeSet<>(List.of(
                new BookKey("0321349601", 1),
                new BookKey("0134685997", 3),
                new BookKey("0134685997", 2),
                new BookKey("0134685997", 3))); // compareTo == 0: treated as a duplicate

        assertThat(sorted)
                .containsExactly(
                        new BookKey("0134685997", 2), new BookKey("0134685997", 3), new BookKey("0321349601", 1));
    }

    @Test
    void comparatorBuildsOtherOrders() {
        List<BookKey> keys = new ArrayList<>(
                List.of(new BookKey("0321349601", 1), new BookKey("0134685997", 3), new BookKey("0201633612", 3)));

        // Newest edition first, then by ISBN. Comparators compose like LINQ's OrderBy/ThenBy.
        keys.sort(Comparator.comparingInt(BookKey::edition).reversed().thenComparing(BookKey::isbn));

        assertThat(keys)
                .containsExactly(
                        new BookKey("0134685997", 3), new BookKey("0201633612", 3), new BookKey("0321349601", 1));
    }

    @Test
    void comparatorCanPlaceNullsExplicitly() {
        List<String> titles = Arrays.asList("Emma", null, "Dune");

        // Natural ordering throws NullPointerException on null; nullsFirst/nullsLast decide instead.
        titles.sort(Comparator.nullsLast(Comparator.naturalOrder()));

        assertThat(titles).containsExactly("Dune", "Emma", null);
    }
}
