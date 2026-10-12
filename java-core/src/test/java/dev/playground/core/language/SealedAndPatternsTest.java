package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Sealed types (Java 17) and pattern matching: {@code instanceof} patterns (16), record patterns
 * and pattern {@code switch} (21), unnamed variables {@code _} (22). Guide: §4.1 Modern language.
 */
class SealedAndPatternsTest {

    @Test
    void instanceofPatternBindsAVariable() {
        Object shape = new Circle(2);

        // Old style: if (shape instanceof Circle) { Circle c = (Circle) shape; ... }
        // The pattern tests and casts in one go; `c` is in scope only where the test is true.
        if (shape instanceof Circle c && c.radius() > 1) {
            assertThat(c.radius()).isEqualTo(2);
        } else {
            throw new AssertionError("expected a circle bigger than 1");
        }
    }

    @Test
    void recordPatternDeconstructsTheComponents() {
        Shape shape = new Rectangle(2, 5);

        // A record pattern pulls the components out directly; `_` ignores the ones you do not need.
        if (shape instanceof Rectangle(double width, double height)) {
            assertThat(width * height).isEqualTo(10);
        } else {
            throw new AssertionError("expected a rectangle");
        }
        // Flow scoping: the else above always throws, so `width` and `height` are still in scope
        // here, and this pattern needs a new name.
        // (`Rectangle(var w, _)` is also legal Java, but Palantir Java Format cannot parse it yet.)
        if (shape instanceof Rectangle(var w, var _)) {
            assertThat(w).isEqualTo(2);
            assertThat(height).isEqualTo(5); // bound by the first pattern

        } else {
            throw new AssertionError("expected a rectangle");
        }
    }

    @Test
    void switchOverASealedTypeIsExhaustive() {
        // Shapes.area has no `default`: the compiler knows Shape permits only these three records.
        assertThat(Shapes.area(new Square(3))).isEqualTo(9);
        assertThat(Shapes.area(new Rectangle(2, 4))).isEqualTo(8);
        assertThat(Shapes.area(new Circle(1))).isEqualTo(Math.PI);
    }

    @Test
    void guardsRefineACase() {
        assertThat(Shapes.describe(new Circle(20))).isEqualTo("large circle");
        assertThat(Shapes.describe(new Circle(1))).isEqualTo("small circle");
        assertThat(Shapes.describe(new Rectangle(3, 3))).isEqualTo("square-shaped rectangle");
        assertThat(Shapes.describe(new Rectangle(3, 4))).isEqualTo("rectangle");
        assertThat(Shapes.describe(new Square(1))).isEqualTo("square");
    }

    @Test
    void switchOnObjectHandlesTypesAndNull() {
        assertThat(classify(null)).isEqualTo("null");
        assertThat(classify(7)).isEqualTo("positive int");
        assertThat(classify(-7)).isEqualTo("int");
        assertThat(classify("abc")).isEqualTo("string of 3");
        assertThat(classify(2.5)).isEqualTo("something else");
    }

    @Test
    void sealedInterfaceKnowsItsPermittedSubclasses() {
        assertThat(Shape.class.isSealed()).isTrue();
        assertThat(Shape.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Circle.class, Square.class, Rectangle.class);
    }

    private static String classify(Object value) {
        // Over Object the switch needs `default` (or an unconditional `case Object o`) to be
        // exhaustive. Without `case null` a null value would throw NullPointerException.
        // The guarded case must come before the plain `Integer i`: the other way round the plain
        // case "dominates" it and the compiler rejects the switch.
        return switch (value) {
            case null -> "null";
            case Integer i when i > 0 -> "positive int";
            case Integer i -> "int";
            case String s -> "string of " + s.length();
            default -> "something else";
        };
    }
}
