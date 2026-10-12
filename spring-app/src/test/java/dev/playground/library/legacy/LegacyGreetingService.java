package dev.playground.library.legacy;

import dev.playground.library.config.LibraryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LEGACY EXAMPLE: the service header you meet in most Lombok codebases. {@code @RequiredArgsConstructor}
 * writes a constructor taking every {@code final} field, which is constructor injection without typing the
 * constructor; {@code @Slf4j} writes {@code private static final Logger log = LoggerFactory.getLogger(...)}.
 * Not a bean (no {@code @Service}): the test builds it by hand. Guide: §6 Legacy.
 */
@Slf4j
@RequiredArgsConstructor
public class LegacyGreetingService {

    private final LibraryProperties properties;

    public String greet(String name) {
        log.info("Greeting {}", name);
        return "Welcome to " + properties.name() + ", " + name;
    }
}
