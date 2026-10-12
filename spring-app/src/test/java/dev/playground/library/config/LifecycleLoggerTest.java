package dev.playground.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * The lifecycle callbacks of {@link LifecycleLogger}, checked through the log output. Guide: §5.1
 * Spring Boot fundamentals.
 */
@ExtendWith(OutputCaptureExtension.class)
class LifecycleLoggerTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(LifecycleLogger.class);

    @Test
    void logsOnStartupAndShutdown(CapturedOutput output) {
        runner.run(context -> assertThat(output).contains("Lifecycle: beans ready"));

        // run(...) closes the context when the lambda returns, which triggers @PreDestroy.
        assertThat(output).contains("Lifecycle: shutting down");
    }

    @Test
    void logsTheReadyMessageWithThePort(CapturedOutput output) {
        // In the real application SpringApplication publishes ApplicationReadyEvent once Tomcat
        // listens, and sets local.server.port. Here both are done by hand.
        runner.withPropertyValues("local.server.port=8080").run(context -> {
            var event = new ApplicationReadyEvent(
                    new SpringApplication(), new String[0], context.getSourceApplicationContext(), Duration.ZERO);
            context.publishEvent(event);

            assertThat(output).contains("Library ready on port 8080");
        });
    }
}
