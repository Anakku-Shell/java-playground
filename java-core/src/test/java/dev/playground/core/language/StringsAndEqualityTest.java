package dev.playground.core.language;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * {@code ==} vs {@code equals}, the string pool, StringBuilder and the Integer cache: the classic
 * traps. Guide: §4.1 Modern language.
 *
 * <p>Rule: {@code ==} compares references (same object?) for every non-primitive type. For value
 * comparison call {@code equals}. C# overloads {@code ==} on string; Java does not.
 */
class StringsAndEqualityTest {

    @Test
    void literalsAreInternedSoDoubleEqualsHappensToWork() {
        // Identical literals share one object from the string pool, so == is true here by accident.
        String a = "java";
        String b = "java";

        assertThat(a == b).isTrue();
        assertThat(a.equals(b)).isTrue();
    }

    @Test
    void newStringIsADifferentObject() {
        String pooled = "java";
        String created = new String("java");

        assertThat(pooled == created).isFalse(); // different objects...
        assertThat(pooled.equals(created)).isTrue(); // ...same characters
        assertThat(created.intern() == pooled).isTrue(); // intern() returns the pooled instance
    }

    @Test
    void runtimeConcatenationBuildsANewString() {
        String pooled = "java";
        String prefix = "ja";
        final String constantPrefix = "ja";

        // Computed at runtime: a new object. This is how `==` bugs slip through tests.
        assertThat((prefix + "va") == pooled).isFalse();
        // A final variable with a literal is a compile-time constant: folded to "java" and pooled.
        assertThat((constantPrefix + "va") == pooled).isTrue();
    }

    @Test
    void stringsAreImmutable() {
        String title = "dune";

        String upper = title.toUpperCase(); // returns a new String; title is unchanged

        assertThat(title).isEqualTo("dune");
        assertThat(upper).isEqualTo("DUNE");
    }

    @Test
    void stringBuilderIsTheMutableOne() {
        // Concatenating in a loop creates a new String each time; StringBuilder appends in place.
        var builder = new StringBuilder();
        for (int i = 1; i <= 3; i++) {
            builder.append(i).append(',');
        }
        builder.setLength(builder.length() - 1); // drop the last comma

        assertThat(builder.toString()).isEqualTo("1,2,3");
        assertThat(builder.reverse().toString()).isEqualTo("3,2,1");
    }

    @Test
    void integerCacheMakesSmallBoxesIdentical() {
        // Autoboxing uses Integer.valueOf, which caches -128..127 (the only range the JLS guarantees;
        // -XX:AutoBoxCacheMax can raise the top). By default each 128 is a new object, so == on
        // Integer "works" in small tests and fails in production.
        Integer small1 = 127;
        Integer small2 = 127;
        Integer big1 = 128;
        Integer big2 = 128;

        assertThat(small1 == small2).isTrue();
        assertThat(big1 == big2).isFalse();
        assertThat(big1.equals(big2)).isTrue();
    }

    @Test
    void unboxingNullThrows() {
        Integer missing = null; // e.g. Map.get of an absent key

        assertThatThrownBy(() -> {
                    int value = missing; // auto-unboxing calls missing.intValue()
                })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void modernStringHelpers() {
        assertThat("   ".isBlank()).isTrue(); // 11
        assertThat("  dune  ".strip()).isEqualTo("dune"); // 11, Unicode-aware trim()
        assertThat("ab".repeat(3)).isEqualTo("ababab"); // 11
        assertThat("a\nb\nc".lines().count()).isEqualTo(3); // 11
        assertThat(String.join(", ", "a", "b")).isEqualTo("a, b"); // 8
    }
}
