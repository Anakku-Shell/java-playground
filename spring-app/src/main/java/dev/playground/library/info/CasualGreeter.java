package dev.playground.library.info;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * The other {@link Greeter}. Picked for a single {@code Greeter} parameter only when it says
 * {@code @Qualifier("casual")} (a {@code List<Greeter>} parameter would receive both greeters).
 * Guide: §5.1 Spring Boot fundamentals.
 */
@Component
@Qualifier("casual")
public class CasualGreeter implements Greeter {

    @Override
    public String greet(String name) {
        return "Hi " + name + "!";
    }
}
