package dev.playground.core.language;

/**
 * A concrete figure that extends the abstract class and overrides a default method.
 * Guide: §4.1 Modern language.
 */
public class Rect extends AbstractFigure {

    private final double width;
    private final double height;

    public Rect(double width, double height) {
        // Since Java 25 (flexible constructor bodies) code may run before super(...), as long as it
        // does not read `this` or call its methods. Before 25, super(...) had to be the very first statement.
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("sides must be positive: " + width + " x " + height);
        }
        super("rect");
        this.width = width;
        this.height = height;
    }

    @Override
    public double area() {
        return width * height;
    }

    @Override
    public String describe() {
        return super.describe() + " (" + width + " x " + height + ")";
    }
}
