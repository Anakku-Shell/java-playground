package dev.playground.core.functional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

/**
 * Lambdas, the standard functional interfaces and method references. A lambda is an implementation
 * of an interface with a single abstract method (a "functional interface"). Guide: §4.2
 * Collections &amp; functional.
 */
class LambdasTest {

    @Test
    void coreFunctionalInterfaces() {
        Function<String, Integer> length = s -> s.length(); // T -> R
        BiFunction<Integer, Integer, Integer> add = (a, b) -> a + b; // (T, U) -> R
        UnaryOperator<String> shout = s -> s.toUpperCase(); // T -> T
        Predicate<String> isEmpty = s -> s.isEmpty(); // T -> boolean
        Supplier<List<String>> newList = () -> new ArrayList<>(); // () -> T
        List<String> seen = new ArrayList<>();
        Consumer<String> remember = s -> seen.add(s); // T -> void

        // Each interface has its own method name: apply, test, get, accept.
        assertThat(length.apply("Dune")).isEqualTo(4);
        assertThat(add.apply(2, 3)).isEqualTo(5);
        assertThat(shout.apply("dune")).isEqualTo("DUNE");
        assertThat(isEmpty.test("")).isTrue();
        assertThat(newList.get()).isEmpty();
        remember.accept("Dune");
        assertThat(seen).containsExactly("Dune");
    }

    @Test
    void functionsCompose() {
        Function<Integer, Integer> plusOne = x -> x + 1;
        Function<Integer, Integer> twice = x -> x * 2;

        // f.andThen(g) = g(f(x)); f.compose(g) = f(g(x)).
        assertThat(plusOne.andThen(twice).apply(3)).isEqualTo(8);
        assertThat(plusOne.compose(twice).apply(3)).isEqualTo(7);
    }

    @Test
    void predicatesCombine() {
        Predicate<String> longTitle = s -> s.length() > 4;
        Predicate<String> startsWithD = s -> s.startsWith("D");

        assertThat(longTitle.and(startsWithD).test("Dracula")).isTrue();
        assertThat(longTitle.or(startsWithD).test("Dune")).isTrue();
        assertThat(longTitle.negate().test("Dune")).isTrue();
        // Predicate.not (Java 11) reads well with method references.
        assertThat(List.of("Dune", "", "Emma").stream()
                        .filter(Predicate.not(String::isEmpty))
                        .toList())
                .containsExactly("Dune", "Emma");
    }

    @Test
    void fourKindsOfMethodReference() {
        String prefix = "Book: ";

        Function<String, Integer> staticRef = Integer::parseInt; // ClassName::staticMethod
        Function<String, String> boundRef = prefix::concat; // instance::method (receiver fixed)
        Function<String, String> unboundRef = String::toUpperCase; // ClassName::instanceMethod
        Function<String, StringBuilder> constructorRef = StringBuilder::new; // ClassName::new

        assertThat(staticRef.apply("42")).isEqualTo(42); // Integer.parseInt("42")
        assertThat(boundRef.apply("Dune")).isEqualTo("Book: Dune"); // prefix.concat("Dune")
        assertThat(unboundRef.apply("dune")).isEqualTo("DUNE"); // "dune".toUpperCase()
        assertThat(constructorRef.apply("ab").reverse().toString()).isEqualTo("ba"); // new StringBuilder("ab")
    }

    @Test
    void ownFunctionalInterfaceAcceptsALambda() {
        PriceRule tenPercentOff = price -> price * 0.9;
        PriceRule minusTwo = price -> price - 2;

        assertThat(tenPercentOff.apply(20)).isEqualTo(18.0);
        assertThat(tenPercentOff.then(minusTwo).apply(20)).isEqualTo(16.0);
    }

    @Test
    void lambdasCaptureEffectivelyFinalVariables() {
        int base = 10; // never reassigned, so "effectively final": a lambda may read it
        Function<Integer, Integer> addBase = x -> x + base;
        assertThat(addBase.apply(1)).isEqualTo(11);

        // int count = 0; list.forEach(s -> count++);  does not compile: count is reassigned.
        // A lambda captures the value, not the variable. To accumulate, mutate an object instead
        // (or better, let the stream do it: count(), sum(), collect()).
        AtomicInteger count = new AtomicInteger();
        List.of("a", "b", "c").forEach(_ -> count.incrementAndGet());
        assertThat(count.get()).isEqualTo(3);
    }

    @Test
    void checkedExceptionsNeedAWrapperInsideLambdas() {
        // Function.apply does not declare IOException, so `Files::readString` cannot be a Function.
        // CheckedFunctions.unchecked adapts it and rethrows as UncheckedIOException.
        Function<Path, String> read = CheckedFunctions.unchecked(Files::readString);

        assertThatThrownBy(() -> read.apply(Path.of("does-not-exist.txt")))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    void wrappingAnInterruptedExceptionRestoresTheInterruptFlag() {
        Function<Long, String> sleepy = CheckedFunctions.unchecked(millis -> {
            throw new InterruptedException("stop");
        });

        try {
            assertThatThrownBy(() -> sleepy.apply(10L)).hasCauseInstanceOf(InterruptedException.class);
            // Catching InterruptedException clears the thread's interrupt flag. Whoever swallows or
            // wraps it must set the flag again, or code further up never learns it should stop.
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clears the flag so the next test on this thread is unaffected
        }
    }
}
