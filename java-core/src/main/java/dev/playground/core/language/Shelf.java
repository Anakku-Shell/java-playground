package dev.playground.core.language;

import java.util.List;

/**
 * The fixed version of {@link LeakyShelf}: a defensive, unmodifiable copy in the constructor.
 * Guide: §4.1 Modern language.
 */
public record Shelf(String label, List<String> titles) {

    public Shelf {
        titles = List.copyOf(titles); // copies, and rejects null elements
    }
}
