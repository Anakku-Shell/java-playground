package dev.playground.library.common;

import java.util.SortedSet;

/** {@code ?sort=} names a property the endpoint does not sort by: 400. Guide: §5.5 Advanced JPA. */
public class InvalidSortException extends RuntimeException {

    public InvalidSortException(String property, SortedSet<String> sortable) {
        super("Cannot sort by '" + property + "'. Sortable: " + String.join(", ", sortable) + ".");
    }
}
