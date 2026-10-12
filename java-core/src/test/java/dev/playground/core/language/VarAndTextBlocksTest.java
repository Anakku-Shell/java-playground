package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

/**
 * Local-variable type inference ({@code var}, Java 10) and text blocks (Java 15).
 * Guide: §4.1 Modern language.
 *
 * <p>Where {@code var} does NOT compile:
 *
 * <pre>
 * var x;                    // no initializer: nothing to infer from
 * var n = null;             // null has no useful type
 * var numbers = {1, 2};     // array initializer needs an explicit type
 * var f = () -> 42;         // a lambda needs a target type (which functional interface?)
 * class A { var field = 1; }            // fields: no
 * void m(var p) {}                      // method parameters: no
 * var m() { return 1; }                 // return types: no
 * </pre>
 *
 * {@code var} is only for local variables (including for-loops and try-with-resources) and lambda
 * parameters. The type is still static: it is fixed at compile time, exactly like C#'s {@code var}.
 */
class VarAndTextBlocksTest {

    @Test
    void varInfersTheStaticType() {
        var titles = new ArrayList<String>(); // ArrayList<String>, not Object

        titles.add("Dune");
        // titles.add(42); would not compile

        for (var title : titles) {
            assertThat(title.length()).isEqualTo(4); // title is a String: length() is available
        }
    }

    @Test
    void varWithDiamondInfersObject() {
        // Gotcha: with no type on either side, the diamond falls back to Object.
        var anything = new ArrayList<>(); // ArrayList<Object>
        anything.add(1);
        anything.add("two");

        assertThat(anything).hasSize(2);
    }

    @Test
    void varInLambdaParameters() {
        // Useful when you want an annotation on a lambda parameter: (@Nonnull var a, ...).
        BiFunction<Integer, Integer, Integer> add = (var a, var b) -> a + b;

        assertThat(add.apply(2, 3)).isEqualTo(5);
    }

    @Test
    void textBlockStripsIncidentIndentation() {
        // The closing """ sets the left margin: the indentation common to all lines is removed.
        String json = """
                {
                  "title": "Dune",
                  "year": 1965
                }
                """;

        assertThat(json).isEqualTo("{\n  \"title\": \"Dune\",\n  \"year\": 1965\n}\n");
    }

    @Test
    void textBlockWorksWithFormatted() {
        String sql = """
                SELECT * FROM books
                WHERE year > %d
                """.formatted(2000);

        assertThat(sql).isEqualTo("SELECT * FROM books\nWHERE year > 2000\n");
    }

    @Test
    void textBlockEscapesJoinLinesAndKeepSpaces() {
        // `\` at the end of a line joins it with the next; `\s` keeps a trailing space
        // that would otherwise be stripped.
        String text = """
                one \
                line
                kept\s
                """;

        assertThat(text).isEqualTo("one line\nkept \n");
        assertThat(List.of(text.split("\n"))).containsExactly("one line", "kept ");
    }
}
