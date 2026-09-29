package dev.playground.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Proves the build runs on the JDK this playground targets. */
class SanityTest {

    @Test
    void runsOnJava25() {
        assertThat(Runtime.version().feature()).isGreaterThanOrEqualTo(25);
    }
}
