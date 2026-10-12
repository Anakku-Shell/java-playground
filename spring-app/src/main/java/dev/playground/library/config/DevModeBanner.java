package dev.playground.library.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * A bean that exists only when the {@code dev} profile is active, e.g. with
 * {@code ./mvnw -pl spring-app spring-boot:run -Dspring-boot.run.profiles=dev} or
 * {@code SPRING_PROFILES_ACTIVE=dev}. Guide: §5.1 Spring Boot fundamentals.
 *
 * <p>An {@link ApplicationRunner} runs once, after the context has started and before the
 * application is reported ready.
 */
@Component
@Profile("dev")
public class DevModeBanner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevModeBanner.class);

    @Override
    public void run(ApplicationArguments args) {
        log.warn("Running with the 'dev' profile: debug logging is on for dev.playground");
    }
}
