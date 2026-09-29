package dev.playground.core.functional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

/**
 * Adapts code that throws checked exceptions to the standard {@link Function}, which cannot. The
 * streams API only accepts the {@code java.util.function} interfaces, so this is the usual way to
 * call something like {@code Files.readString} inside {@code map}. Guide: §4.2 Collections &amp;
 * functional.
 */
public final class CheckedFunctions {

    private CheckedFunctions() {}

    public static <T, R> Function<T, R> unchecked(ThrowingFunction<T, R> function) {
        return value -> {
            try {
                return function.apply(value);
            } catch (IOException e) {
                throw new UncheckedIOException(e); // the JDK's own wrapper for IOException
            } catch (InterruptedException e) {
                // Catching it cleared the thread's interrupt flag: set it again before wrapping.
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (RuntimeException e) {
                throw e; // already unchecked: do not wrap twice
            } catch (Exception e) {
                throw new RuntimeException(e); // no more specific JDK wrapper fits every case
            }
        };
    }
}
