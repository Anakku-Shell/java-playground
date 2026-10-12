package dev.playground.core.functional;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.partitioningBy;
import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.core.language.Genre;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The Streams API: a pipeline of a source, lazy intermediate operations and one terminal operation.
 * The closest .NET relative is LINQ to Objects. Guide: §4.2 Collections &amp; functional.
 */
class StreamsTest {

    private final List<BookSample> books = BookSamples.all();

    @Test
    void filterMapSortedLimit() {
        List<String> titles = books.stream() // source
                .filter(b -> b.year() > 1950) // Where
                .sorted(Comparator.comparingInt(BookSample::year)) // OrderBy
                .map(BookSample::title) // Select
                .limit(3) // Take
                .toList(); // terminal: runs the pipeline

        assertThat(titles).containsExactly("The Fellowship of the Ring", "Dune", "Dune Messiah");
    }

    @Test
    void toListIsUnmodifiable() {
        List<String> titles = books.stream().map(BookSample::title).toList();

        assertThatThrownBy(() -> titles.add("x")).isInstanceOf(UnsupportedOperationException.class);

        // Need to add to the result? Ask for a specific collection. (Collectors.toList() happens to
        // return an ArrayList today, but the Javadoc does not promise mutability.)
        ArrayList<String> mutable = books.stream().map(BookSample::title).collect(toCollection(ArrayList::new));
        mutable.add("x");
        assertThat(mutable).hasSize(books.size() + 1);
    }

    @Test
    void groupingByCountsPerKey() {
        // GroupBy + Count. The map factory keeps the result ordered by the enum.
        Map<Genre, Long> perGenre =
                books.stream().collect(groupingBy(BookSample::genre, () -> new EnumMap<>(Genre.class), counting()));

        assertThat(perGenre)
                .containsExactly(
                        Map.entry(Genre.FICTION, 3L),
                        Map.entry(Genre.SCIENCE, 2L),
                        Map.entry(Genre.HISTORY, 1L),
                        Map.entry(Genre.FANTASY, 2L));

        // A downstream collector decides what each group holds: here, just the titles.
        TreeMap<String, List<String>> titlesByAuthor = books.stream()
                .collect(groupingBy(BookSample::author, TreeMap::new, mapping(BookSample::title, Collectors.toList())));

        assertThat(titlesByAuthor.get("Frank Herbert")).containsExactly("Dune", "Dune Messiah");
        assertThat(titlesByAuthor.firstKey()).isEqualTo("Carl Sagan");
    }

    @Test
    void partitioningBySplitsInTwo() {
        Map<Boolean, List<String>> longBooks = books.stream()
                .collect(partitioningBy(b -> b.pages() > 400, mapping(BookSample::title, Collectors.toList())));

        assertThat(longBooks.get(true)).containsExactly("Emma", "Dune", "The Fellowship of the Ring", "SPQR");
        assertThat(longBooks.get(false)).hasSize(4);
    }

    @Test
    void joiningBuildsAString() {
        String authors = books.stream()
                .map(BookSample::author)
                .distinct()
                .sorted()
                .limit(3)
                .collect(joining(", ", "[", "]"));

        assertThat(authors).isEqualTo("[Carl Sagan, Frank Herbert, J.R.R. Tolkien]");
    }

    @Test
    void flatMapFlattensNestedLists() {
        // SelectMany: each shelf becomes a stream of titles, and flatMap concatenates them.
        List<List<String>> shelves = List.of(List.of("Dune", "Emma"), List.of(), List.of("SPQR"));

        assertThat(shelves.stream().flatMap(List::stream).toList()).containsExactly("Dune", "Emma", "SPQR");
    }

    @Test
    void reduceAndSpecialisedSums() {
        int viaReduce = books.stream().map(BookSample::pages).reduce(0, Integer::sum); // Aggregate
        int viaSum = books.stream().mapToInt(BookSample::pages).sum(); // IntStream: no boxing
        IntSummaryStatistics stats = books.stream().mapToInt(BookSample::pages).summaryStatistics();

        assertThat(viaReduce).isEqualTo(3060).isEqualTo(viaSum);
        assertThat(stats.getMin()).isEqualTo(212);
        assertThat(stats.getMax()).isEqualTo(608);
        assertThat(stats.getAverage()).isEqualTo(382.5);
        // max over an empty stream has no answer, hence Optional (see OptionalTest).
        assertThat(books.stream().max(Comparator.comparingInt(BookSample::pages)))
                .map(BookSample::title)
                .contains("SPQR");
    }

    @Test
    void intStreamRange() {
        assertThat(IntStream.range(0, 5).sum()).isEqualTo(10); // 0..4, end exclusive
        assertThat(IntStream.rangeClosed(1, 5).sum()).isEqualTo(15); // 1..5
        assertThat(IntStream.range(0, 3).boxed().toList()).containsExactly(0, 1, 2);
    }

    @Test
    void streamsAreLazyAndShortCircuit() {
        AtomicInteger visited = new AtomicInteger();

        var first = books.stream()
                .peek(_ -> visited.incrementAndGet()) // peek: for debugging, as here
                .filter(b -> b.year() > 1960)
                .findFirst();

        // Elements flow one at a time through the whole pipeline. findFirst stops at Dune, the
        // second book, so the other six are never looked at.
        assertThat(first).map(BookSample::title).contains("Dune");
        assertThat(visited.get()).isEqualTo(2);
    }

    @Test
    void nothingRunsWithoutATerminalOperation() {
        AtomicInteger visited = new AtomicInteger();

        Stream<BookSample> pipeline = books.stream().peek(_ -> visited.incrementAndGet());

        assertThat(visited.get()).isZero(); // only a recipe so far
        assertThat(pipeline.toList()).hasSize(books.size());
        assertThat(visited.get()).isEqualTo(books.size()); // the terminal operation ran it
    }

    @Test
    void countMaySkipThePipelineWhenTheSizeIsKnown() {
        AtomicInteger visited = new AtomicInteger();

        long count = books.stream().peek(_ -> visited.incrementAndGet()).count();

        // Since Java 9, count() on a source of known size (a List) with no filter in between just
        // returns the size: peek/map never run. Do not put side effects in a pipeline.
        assertThat(count).isEqualTo(books.size());
        assertThat(visited.get()).isZero();
    }

    @Test
    void aStreamCanBeConsumedOnlyOnce() {
        Stream<BookSample> stream = books.stream();
        stream.count();

        // Unlike an IEnumerable, a stream is not re-enumerable. Keep the list, call stream() again.
        assertThatThrownBy(stream::count)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been operated upon or closed");
    }

    @Test
    void toMapThrowsOnDuplicateKeysUnlessMerged() {
        assertThatThrownBy(() -> books.stream().collect(toMap(BookSample::author, BookSample::title)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate key");

        Map<String, String> merged = books.stream()
                .collect(toMap(BookSample::author, BookSample::title, (first, second) -> first + " | " + second));

        assertThat(merged.get("Frank Herbert")).isEqualTo("Dune | Dune Messiah");
    }
}
