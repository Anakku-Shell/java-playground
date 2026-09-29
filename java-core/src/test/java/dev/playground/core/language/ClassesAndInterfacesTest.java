package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Classes, interfaces and abstract classes. Guide: §4.1 Modern language.
 *
 * <p>Access modifiers, from most to least open:
 *
 * <pre>
 * modifier        same class  same package  subclass (other pkg)  anywhere
 * public              yes         yes            yes                yes
 * protected           yes         yes            yes                no
 * (none) "package"    yes         yes            no                 no
 * private             yes         no             no                 no
 * </pre>
 *
 * Unlike C#, the default (no modifier) is package-private, not private, and there is no
 * {@code internal}: the package is the unit of encapsulation.
 */
class ClassesAndInterfacesTest {

    @Test
    void constructorsChainWithThis() {
        // Counter(String) delegates to Counter(String, int) with this(name, 0).
        assertThat(new Counter("a").value()).isZero();
        assertThat(new Counter("b", 5).value()).isEqualTo(5);
    }

    @Test
    void constructorCanValidateBeforeCallingSuper() {
        // Java 25 (flexible constructor bodies): Rect checks its arguments before super(...),
        // so an invalid Rect fails fast without running the superclass constructor at all.
        assertThatThrownBy(() -> new Rect(-1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sides must be positive: -1.0 x 2.0");
    }

    @Test
    void overloadedMethodsArePickedByTheArguments() {
        var counter = new Counter("pages");

        counter.increment(); // increment()
        counter.increment(10); // increment(int)

        assertThat(counter.value()).isEqualTo(11);
    }

    @Test
    void staticFieldsAreSharedByAllInstances() {
        // A static field belongs to the class, so every constructor call bumps the same number.
        int before = Counter.created();

        new Counter("x");
        new Counter("y");

        assertThat(Counter.created()).isEqualTo(before + 2);
    }

    @Test
    void defaultMethodIsInheritedAndCanBeOverridden() {
        Figure rect = new Rect(2, 3);

        // Rect overrides describe() and reuses the inherited default through super.describe().
        assertThat(rect.describe()).isEqualTo("rect with area 6.00 (2.0 x 3.0)");
        assertThat(rect.toString()).isEqualTo(rect.describe());
    }

    @Test
    void staticInterfaceMethodIsCalledOnTheInterface() {
        // Static interface methods are not inherited: you call Figure.totalArea, never rect.totalArea.
        double total = Figure.totalArea(List.of(new Rect(2, 3), new Rect(1, 1)));

        assertThat(total).isEqualTo(7.0);
    }

    @Test
    void abstractClassHoldsSharedCodeButCannotBeInstantiated() {
        // `new AbstractFigure("x")` does not compile. An abstract class can have state and
        // constructors (an interface cannot), but a class can extend only one of them.
        assertThat(Modifier.isAbstract(AbstractFigure.class.getModifiers())).isTrue();
        assertThat(new Rect(1, 1).name()).isEqualTo("rect");
    }

    @Test
    void anonymousClassImplementsAnInterfaceInline() {
        // Figure has two abstract methods, so a lambda cannot implement it; an anonymous class can.
        Figure unit = new Figure() {
            @Override
            public double area() {
                return 1;
            }

            @Override
            public String name() {
                return "unit";
            }
        };

        assertThat(unit.describe()).isEqualTo("unit with area 1.00");
    }
}
