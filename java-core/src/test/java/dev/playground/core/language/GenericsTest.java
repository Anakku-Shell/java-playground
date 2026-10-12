package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

/** Generic classes, generic methods, wildcards and type erasure. Guide: §4.1 Modern language. */
class GenericsTest {

    @Test
    void genericClassKeepsTheTypeWithoutCasts() {
        Box<String> title = new Box<>("Dune"); // <> (diamond) infers <String>

        String value = title.get(); // no cast needed: the compiler knows it is a String
        Box<Integer> length = title.map(String::length);

        assertThat(value).isEqualTo("Dune");
        assertThat(length.get()).isEqualTo(4);
    }

    @Test
    void genericMethodInfersItsTypeParameter() {
        // T is inferred from the argument: Integer in the first call, String in the second.
        assertThat(GenericMethods.max(List.of(3, 9, 2))).isEqualTo(9);
        assertThat(GenericMethods.max(List.of("pear", "apple"))).isEqualTo("pear");
    }

    @Test
    void maxOfEmptyListThrows() {
        assertThatThrownBy(() -> GenericMethods.max(List.<Integer>of())).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void extendsWildcardLetsYouRead() {
        // List<Integer> is NOT a List<Number>, but it is a List<? extends Number>.
        // "Producer extends": you can read Numbers out of it, but not add to it.
        List<Integer> ints = List.of(1, 2);
        List<Double> doubles = List.of(1.5, 2.5);

        assertThat(GenericMethods.sum(ints)).isEqualTo(3.0);
        assertThat(GenericMethods.sum(doubles)).isEqualTo(4.0);
    }

    @Test
    void superWildcardLetsYouWrite() {
        // "Consumer super": a List<? super Integer> accepts Integers, whatever its exact type is.
        List<Number> numbers = new ArrayList<>();
        List<Object> objects = new ArrayList<>();

        GenericMethods.fillWithIntegers(numbers, 3);
        GenericMethods.fillWithIntegers(objects, 2);

        assertThat(numbers).containsExactly(0, 1, 2);
        assertThat(objects).containsExactly(0, 1);
    }

    @Test
    void typeErasureMakesAllArrayListsOneClass() {
        // Generics exist only at compile time. At runtime both are just ArrayList, which is why
        // `new T()`, `T.class` and `obj instanceof List<String>` (obj being an Object) do not compile.
        Class<?> strings = new ArrayList<String>().getClass();
        Class<?> integers = new ArrayList<Integer>().getClass();

        assertThat(strings).isSameAs(integers);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void rawTypesDefeatTheCompilerChecks() {
        // A raw type (List without <...>) is pre-Java-5 style. The compiler only warns,
        // and the mistake blows up later, far from where it was made.
        List raw = new ArrayList<String>();
        raw.add(42);
        List<String> strings = raw;

        assertThatThrownBy(() -> {
                    String first = strings.get(0); // the hidden cast to String fails here
                })
                .isInstanceOf(ClassCastException.class);
    }
}
