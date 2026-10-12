package dev.playground.core.language;

import java.util.function.Function;

/**
 * A generic class: {@code T} is a type parameter fixed by the caller ({@code Box<String>}).
 * Guide: §4.1 Modern language.
 */
public final class Box<T> {

    private final T value;

    public Box(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    /** A generic method inside a generic class: R is chosen per call. */
    public <R> Box<R> map(Function<? super T, ? extends R> mapper) {
        return new Box<>(mapper.apply(value));
    }
}
