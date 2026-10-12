package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checked vs unchecked exceptions, try-with-resources, suppressed exceptions and finally.
 * Guide: §4.1 Modern language.
 *
 * <pre>
 * Throwable
 * ├── Error              (OutOfMemoryError...: do not catch)
 * └── Exception          checked: must be caught or declared with `throws`
 *     └── RuntimeException   unchecked: no declaration needed (C# only has this kind)
 * </pre>
 */
class ExceptionsTest {

    @Test
    void checkedExceptionsMustBeCaughtOrDeclared() {
        // Calling borrow() without try/catch, from a method with no `throws`, does not compile.
        try {
            borrow("0134685997");
            throw new AssertionError("borrow should have thrown");
        } catch (BookNotAvailableException e) {
            assertThat(e.isbn()).isEqualTo("0134685997");
            assertThat(e).hasMessage("Book 0134685997 is not available");
        }
    }

    @Test
    void uncheckedExceptionsNeedNoDeclaration() {
        assertThatThrownBy(() -> validateDays(-1))
                .isInstanceOf(InvalidLoanException.class)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("days must be positive: -1");
    }

    @Test
    void wrappingACheckedExceptionKeepsTheCause() {
        // The java.util.function interfaces (and so stream lambdas) declare no checked exceptions,
        // so checked ones get wrapped in unchecked ones.
        // Always pass the original as the cause, or the stack trace is lost.
        assertThatThrownBy(() -> readOrWrap(Path.of("does-not-exist.txt")))
                .isInstanceOf(UncheckedIOException.class)
                .hasCauseInstanceOf(NoSuchFileException.class);
    }

    @Test
    void tryWithResourcesClosesInReverseOrder() {
        List<String> log = new ArrayList<>();

        // Anything AutoCloseable can go in the parentheses; close() runs automatically,
        // last opened first closed, even if the body throws. C#'s `using` is the same idea.
        try (var first = new TrackedResource("a", log);
                var second = new TrackedResource("b", log)) {
            log.add("work");
        }

        assertThat(log).containsExactly("open a", "open b", "work", "close b", "close a");
    }

    @Test
    void closeFailureIsSuppressedNotLost() {
        List<String> log = new ArrayList<>();

        Throwable thrown = catchThrowable(() -> {
            try (var _ = new TrackedResource("db", log, true)) { // `_`: never referenced
                throw new IllegalStateException("work failed");
            }
        });

        // The body's exception wins; the one from close() is attached, not swallowed.
        assertThat(thrown).hasMessage("work failed");
        assertThat(thrown.getSuppressed()).hasSize(1);
        assertThat(thrown.getSuppressed()[0]).hasMessage("close db failed");
    }

    @Test
    void finallyRunsEvenAfterReturn() {
        List<String> log = new ArrayList<>();

        String result = returnFromTry(log);

        // The return value is computed first, then finally runs, then the method returns.
        // Never `return` inside finally: it would silently discard any exception.
        assertThat(result).isEqualTo("from try");
        assertThat(log).containsExactly("try", "finally");
    }

    @Test
    void multiCatchWithAnUnnamedVariable() {
        assertThat(ratio("10", "2")).isEqualTo(5);
        assertThat(ratio("ten", "2")).isEqualTo(-1); // NumberFormatException
        assertThat(ratio("10", "0")).isEqualTo(-1); // ArithmeticException
    }

    private static void borrow(String isbn) throws BookNotAvailableException {
        throw new BookNotAvailableException(isbn);
    }

    private static void validateDays(int days) {
        if (days <= 0) {
            throw new InvalidLoanException("days must be positive: " + days);
        }
    }

    private static String readOrWrap(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String returnFromTry(List<String> log) {
        try {
            log.add("try");
            return "from try";
        } finally {
            log.add("finally");
        }
    }

    private static int ratio(String dividend, String divisor) {
        try {
            return Integer.parseInt(dividend) / Integer.parseInt(divisor);
        } catch (NumberFormatException | ArithmeticException _) { // `_`: the exception is not used
            return -1;
        }
    }
}
