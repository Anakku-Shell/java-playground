package dev.playground.core.language;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Generic methods, bounded type parameters and wildcards (PECS: Producer Extends, Consumer
 * Super). Guide: §4.1 Modern language.
 */
public final class GenericMethods {

    private GenericMethods() {} // utility class: no instances

    /** {@code T extends Comparable<T>}: T must know how to compare itself, so compareTo is available. */
    public static <T extends Comparable<T>> T max(List<T> items) {
        if (items.isEmpty()) {
            throw new NoSuchElementException("max of an empty list");
        }
        T best = items.getFirst(); // getFirst() since Java 21 (SequencedCollection)
        for (T item : items) {
            if (item.compareTo(best) > 0) {
                best = item;
            }
        }
        return best;
    }

    /** The list only produces values, so {@code ? extends Number} accepts List<Integer>, List<Double>... */
    public static double sum(List<? extends Number> numbers) {
        double total = 0;
        for (Number n : numbers) {
            total += n.doubleValue();
        }
        return total;
    }

    /** The list only consumes Integers, so {@code ? super Integer} accepts List<Number>, List<Object>... */
    public static void fillWithIntegers(List<? super Integer> target, int count) {
        for (int i = 0; i < count; i++) {
            target.add(i);
        }
    }
}
