package dev.playground.library.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one source of "now". Code that needs the time takes a {@code Clock} instead of calling
 * {@code Instant.now()}, so a test can pass a fixed clock and assert exact dates (a due date,
 * an audit timestamp). The system default zone decides what "today" is. Guide: §5.5 Advanced JPA.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
