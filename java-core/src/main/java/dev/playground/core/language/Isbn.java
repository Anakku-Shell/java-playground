package dev.playground.core.language;

import java.util.Objects;

/**
 * A record with a compact constructor that validates and normalises its component.
 * Guide: §4.1 Modern language.
 *
 * <p>Simplified on purpose: a real ISBN-10 may end in "X" and both forms carry a check digit.
 */
public record Isbn(String value) {

    // Compact constructor: no parameter list. It runs before the fields are assigned, so
    // reassigning the parameter `value` changes what gets stored.
    public Isbn {
        Objects.requireNonNull(value, "value");
        value = value.replace("-", "");
        if (!value.matches("\\d{10}|\\d{13}")) {
            throw new IllegalArgumentException("ISBN must have 10 or 13 digits: " + value);
        }
    }

    // Records can have extra methods (and static fields), but no extra instance fields.
    public boolean isIsbn13() {
        return value.length() == 13;
    }
}
