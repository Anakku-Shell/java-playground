package dev.playground.core.language;

import java.util.List;
import java.util.Locale;

/**
 * An interface with abstract, {@code default} and {@code static} methods (default/static since
 * Java 8). Guide: §4.1 Modern language.
 */
public interface Figure {

    double area();

    String name();

    /** Inherited by every implementation, which may override it. */
    default String describe() {
        // Locale.ROOT: with the default locale a Spanish machine would print "6,00".
        return String.format(Locale.ROOT, "%s with area %.2f", name(), area());
    }

    /** Belongs to the interface itself: called as Figure.totalArea(...), never inherited. */
    static double totalArea(List<? extends Figure> figures) {
        double total = 0;
        for (Figure figure : figures) {
            total += figure.area();
        }
        return total;
    }
}
