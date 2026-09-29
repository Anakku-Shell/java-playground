package dev.playground.library;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Starts the whole ApplicationContext once. If any bean or configuration is broken, this is the
 * test that fails first.
 */
@SpringBootTest
class LibraryApplicationTest {

    @Test
    void contextLoads() {}
}
