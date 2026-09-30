package dev.playground.library.info;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * The default {@link Greeter}: {@code @Primary} makes it the winner when a constructor asks for "a
 * Greeter" and several beans qualify. Guide: §5.1 Spring Boot fundamentals.
 */
@Component
@Primary
public class FormalGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Good day, " + name + ".";
    }
}
