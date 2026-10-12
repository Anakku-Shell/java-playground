package dev.playground.core.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * {@link Optional}: a return type that says "there may be no result" in the signature instead of
 * returning null. Guide: §4.2 Collections &amp; functional.
 *
 * <p>Use it for return values only. Not for fields (it is not serializable and adds a wrapper per
 * object), not for parameters (callers then pass {@code null} Optionals), and not as a wrapper
 * around a collection: return an empty list instead of {@code Optional<List<T>>}.
 */
class OptionalTest {

    private final BookCatalog catalog = new BookCatalog(BookSamples.all());

    @Test
    void presentAndEmpty() {
        Optional<BookSample> dune = catalog.findByTitle("Dune");
        Optional<BookSample> missing = catalog.findByTitle("Ulysses");

        assertThat(dune.isPresent()).isTrue();
        assertThat(missing.isEmpty()).isTrue();
        assertThat(missing).isEqualTo(Optional.empty());
    }

    @Test
    void mapTransformsTheValueIfThereIsOne() {
        // Like ?. in C#: the function runs only when a value is present.
        assertThat(catalog.findByTitle("Dune").map(BookSample::author)).contains("Frank Herbert");
        assertThat(catalog.findByTitle("Ulysses").map(BookSample::author)).isEmpty();
    }

    @Test
    void flatMapAvoidsNestedOptionals() {
        // findFirstByAuthor already returns an Optional. map would give Optional<Optional<...>>.
        Optional<String> firstByDunesAuthor = catalog.findByTitle("Dune Messiah")
                .map(BookSample::author)
                .flatMap(catalog::findFirstByAuthor)
                .map(BookSample::title);

        assertThat(firstByDunesAuthor).contains("Dune");
    }

    @Test
    void filterKeepsTheValueOrEmptiesIt() {
        assertThat(catalog.findByTitle("SPQR").filter(b -> b.pages() > 500)).isPresent();
        assertThat(catalog.findByTitle("Cosmos").filter(b -> b.pages() > 500)).isEmpty();
    }

    @Test
    void orElseIsEagerOrElseGetIsLazy() {
        AtomicInteger calls = new AtomicInteger();

        String title = catalog.findByTitle("Dune").map(BookSample::title).orElse(expensiveDefault(calls));
        // The argument of orElse is evaluated before the call, even though Dune was found.
        assertThat(title).isEqualTo("Dune");
        assertThat(calls.get()).isEqualTo(1);

        String again = catalog.findByTitle("Dune").map(BookSample::title).orElseGet(() -> expensiveDefault(calls));
        // orElseGet takes a Supplier and only calls it when the Optional is empty.
        assertThat(again).isEqualTo("Dune");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void orElseThrowForMissingValues() {
        assertThatThrownBy(() -> catalog.findByTitle("Ulysses").orElseThrow())
                .isInstanceOf(NoSuchElementException.class);
        // The usual form in a service: turn "not found" into a domain exception.
        assertThatThrownBy(() -> catalog.findByTitle("Ulysses")
                        .orElseThrow(() -> new IllegalArgumentException("No book titled Ulysses")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("No book titled Ulysses");
    }

    @Test
    void ofNullableWrapsAPossiblyNullValue() {
        String nothing = null;

        assertThat(Optional.ofNullable(nothing)).isEmpty();
        assertThatThrownBy(() -> Optional.of(nothing)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void ifPresentOrElseRunsOneBranch() {
        List<String> log = new ArrayList<>();

        catalog.findByTitle("Emma").ifPresentOrElse(b -> log.add("found " + b.title()), () -> log.add("none"));
        catalog.findByTitle("Ulysses").ifPresentOrElse(b -> log.add("found " + b.title()), () -> log.add("none"));

        assertThat(log).containsExactly("found Emma", "none");
    }

    private static String expensiveDefault(AtomicInteger calls) {
        calls.incrementAndGet();
        return "Untitled";
    }
}
