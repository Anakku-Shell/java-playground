package dev.playground.core.functional;

/**
 * Like {@link java.util.function.Function}, but {@code apply} may throw a checked exception.
 * Guide: §4.2 Collections &amp; functional.
 */
@FunctionalInterface
public interface ThrowingFunction<T, R> {

    R apply(T value) throws Exception;
}
