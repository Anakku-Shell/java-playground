package dev.playground.library.info;

import dev.playground.library.config.LibraryProperties;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shows configuration and dependency injection at work. Guide: §5.1 Spring Boot fundamentals.
 *
 * <p>Everything arrives through the one constructor (no {@code @Autowired} needed): the fields can
 * be final, the dependencies are visible in the signature, the object is never half-built, and a
 * plain unit test can create it with {@code new}.
 */
@RestController
@RequestMapping("/api/info")
public class InfoController {

    private final LibraryProperties properties;
    private final Environment environment;
    private final Greeter greeter;
    private final Greeter casualGreeter;
    private final String applicationName;

    public InfoController(
            LibraryProperties properties,
            Environment environment,
            Greeter greeter, // two beans match; @Primary FormalGreeter wins
            @Qualifier("casual") Greeter casualGreeter,
            // @Value reads one property by key. Fine for a single value; for groups of settings
            // prefer a @ConfigurationProperties record like LibraryProperties.
            @Value("${spring.application.name}") String applicationName) {
        this.properties = properties;
        this.environment = environment;
        this.greeter = greeter;
        this.casualGreeter = casualGreeter;
        this.applicationName = applicationName;
    }

    @GetMapping
    public LibraryInfoResponse info() {
        return new LibraryInfoResponse(
                applicationName,
                properties.name(),
                properties.loans().maxActive(),
                properties.loans().durationDays(),
                List.of(environment.getActiveProfiles()));
    }

    @GetMapping("/greetings")
    public GreetingsResponse greetings(@RequestParam(defaultValue = "reader") String name) {
        return new GreetingsResponse(greeter.greet(name), casualGreeter.greet(name));
    }
}
