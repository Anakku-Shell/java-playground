package dev.playground.core.language;

import java.util.Objects;

/**
 * An abstract class: it can hold state and constructors (an interface cannot), but it cannot be
 * instantiated and a class can extend only one. Guide: §4.1 Modern language.
 */
public abstract class AbstractFigure implements Figure {

    private final String name;

    // protected: only subclasses (and this package) can call it.
    protected AbstractFigure(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    // final: subclasses cannot override it.
    @Override
    public final String name() {
        return name;
    }

    @Override
    public String toString() {
        return describe();
    }
}
