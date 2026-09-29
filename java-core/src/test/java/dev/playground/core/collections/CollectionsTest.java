package dev.playground.core.collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The collection family: interfaces ({@code List}, {@code Set}, {@code Map}) and the classes behind
 * them. Declare variables with the interface, pick the class for its behaviour. Guide: §4.2
 * Collections &amp; functional.
 */
class CollectionsTest {

    @Test
    void listKeepsInsertionOrderAndAllowsDuplicates() {
        List<String> titles = new ArrayList<>();
        titles.add("Dune");
        titles.add("Emma");
        titles.add("Dune");

        assertThat(titles).containsExactly("Dune", "Emma", "Dune");
        assertThat(titles.get(1)).isEqualTo("Emma");
        assertThat(titles.indexOf("Dune")).isZero();
    }

    @Test
    void arrayListVsLinkedList() {
        // ArrayList: a resizable array. get(i) is O(1); inserting in the middle shifts elements.
        // LinkedList: a doubly linked list. get(i) walks the nodes (O(n)). In practice ArrayList
        // (or ArrayDeque for queues) wins almost always; LinkedList mostly shows up in old code.
        List<String> array = new ArrayList<>(List.of("b", "c"));
        LinkedList<String> linked = new LinkedList<>(List.of("b", "c"));

        array.addFirst("a"); // Java 21: List.addFirst/getLast/reversed (SequencedCollection)
        linked.addFirst("a");

        assertThat(array).isEqualTo(linked); // List.equals compares elements, not the class
        // LinkedList is also a Deque: a double-ended queue.
        Deque<String> deque = linked;
        assertThat(deque.pollLast()).isEqualTo("c");
        assertThat(deque.peekFirst()).isEqualTo("a");
    }

    @Test
    void setsDifferInIterationOrder() {
        List<String> input = List.of("pear", "apple", "fig", "apple");

        Set<String> hash = new HashSet<>(input); // no order guarantee at all
        Set<String> linked = new LinkedHashSet<>(input); // insertion order
        Set<String> tree = new TreeSet<>(input); // sorted (natural order or a Comparator)

        assertThat(hash).containsExactlyInAnyOrder("pear", "apple", "fig"); // duplicates dropped
        assertThat(linked).containsExactly("pear", "apple", "fig");
        assertThat(tree).containsExactly("apple", "fig", "pear");
    }

    @Test
    void mapsDifferInIterationOrder() {
        Map<String, Integer> hash = new HashMap<>();
        Map<String, Integer> linked = new LinkedHashMap<>();
        TreeMap<String, Integer> tree = new TreeMap<>();
        for (Map<String, Integer> map : List.of(hash, linked, tree)) {
            map.put("pear", 3);
            map.put("apple", 5);
            map.put("fig", 1);
        }

        assertThat(hash.keySet()).containsExactlyInAnyOrder("pear", "apple", "fig");
        assertThat(linked.keySet()).containsExactly("pear", "apple", "fig");
        assertThat(tree.keySet()).containsExactly("apple", "fig", "pear");
        // A TreeMap is also a NavigableMap: range queries come for free.
        assertThat(tree.firstKey()).isEqualTo("apple");
        assertThat(tree.headMap("g")).containsOnlyKeys("apple", "fig");
    }

    @Test
    void factoryCollectionsAreUnmodifiable() {
        List<String> fixed = List.of("a", "b");

        // The type is still List: the compiler lets you call add, and it fails at runtime.
        assertThatThrownBy(() -> fixed.add("c")).isInstanceOf(UnsupportedOperationException.class);
        // The factories also reject nulls, unlike ArrayList.
        assertThatThrownBy(() -> List.of("a", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Map.of("k", 1, "k", 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unmodifiableViewSeesChangesButCopyDoesNot() {
        List<String> source = new ArrayList<>(List.of("a"));
        List<String> view = Collections.unmodifiableList(source); // read-only window on source
        List<String> copy = List.copyOf(source); // independent snapshot

        source.add("b");

        assertThat(view).containsExactly("a", "b");
        assertThat(copy).containsExactly("a");
        assertThatThrownBy(() -> view.add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mapHelpersAvoidTheGetCheckPutDance() {
        Map<String, Integer> stock = new HashMap<>(Map.of("Dune", 2));

        assertThat(stock.getOrDefault("Emma", 0)).isZero();
        stock.putIfAbsent("Dune", 99); // key present: nothing changes
        stock.merge("Dune", 1, Integer::sum); // present: 2 + 1
        stock.merge("Emma", 1, Integer::sum); // absent: just puts 1

        assertThat(stock).containsExactlyInAnyOrderEntriesOf(Map.of("Dune", 3, "Emma", 1));

        // computeIfAbsent builds a multimap: create the list the first time a key shows up.
        Map<String, List<String>> byAuthor = new TreeMap<>();
        byAuthor.computeIfAbsent("Herbert", _ -> new ArrayList<>()).add("Dune");
        byAuthor.computeIfAbsent("Herbert", _ -> new ArrayList<>()).add("Dune Messiah");
        byAuthor.computeIfAbsent("Austen", _ -> new ArrayList<>()).add("Emma");

        assertThat(byAuthor)
                .containsExactly(
                        Map.entry("Austen", List.of("Emma")), Map.entry("Herbert", List.of("Dune", "Dune Messiah")));
    }

    @Test
    void removingInsideForEachThrowsButRemoveIfWorks() {
        List<Integer> numbers = new ArrayList<>(List.of(1, 2, 3, 4));

        // for-each uses an Iterator; changing the list behind its back is detected ("fail-fast").
        // Fail-fast is best effort: removing the second-to-last element ends the loop before the
        // check runs, so nothing is thrown. Here 2 is removed early, so the next step detects it.
        // (remove(n) with an Integer calls remove(Object), not remove(int index).)
        assertThatThrownBy(() -> {
                    for (Integer n : numbers) {
                        if (n % 2 == 0) {
                            numbers.remove(n);
                        }
                    }
                })
                .isInstanceOf(ConcurrentModificationException.class);

        List<Integer> fresh = new ArrayList<>(List.of(1, 2, 3, 4));
        fresh.removeIf(n -> n % 2 == 0);
        assertThat(fresh).containsExactly(1, 3);
    }

    @Test
    void arraysAsListIsFixedSizeButWritable() {
        String[] array = {"a", "b"};
        List<String> list = Arrays.asList(array); // a List view over the array

        list.set(0, "z"); // writes through to the array
        assertThat(array[0]).isEqualTo("z");
        assertThatThrownBy(() -> list.add("c")).isInstanceOf(UnsupportedOperationException.class);
    }
}
