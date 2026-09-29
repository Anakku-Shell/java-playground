package dev.playground.core.language;

/**
 * Pattern-matching switches over the sealed {@link Shape}. Guide: §4.1 Modern language.
 *
 * <p>This is the "data-oriented" style: the operations live outside the types and the compiler
 * checks exhaustiveness. Adding a fourth Shape breaks the build here until it is handled.
 */
public final class Shapes {

    private Shapes() {}

    public static double area(Shape shape) {
        return switch (shape) {
            case Circle c -> Math.PI * c.radius() * c.radius();
            case Square s -> s.side() * s.side();
            case Rectangle(double width, double height) -> width * height;
        };
    }

    public static String describe(Shape shape) {
        // Cases are checked top to bottom. A guarded case must precede its unguarded twin, or the
        // compiler reports it as dominated (unreachable).
        return switch (shape) {
            case Circle c when c.radius() > 10 -> "large circle";
            case Circle c -> "small circle";
            case Rectangle(var width, var height) when width == height -> "square-shaped rectangle";
            case Rectangle _ -> "rectangle"; // `_`: matched but not used (Java 22)
            case Square _ -> "square";
        };
    }
}
