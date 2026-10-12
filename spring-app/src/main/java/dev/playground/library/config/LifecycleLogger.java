package dev.playground.library.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Logs the moments of a bean's and the application's life. Run the app and read the console to
 * see the order. Guide: §5.1 Spring Boot fundamentals.
 *
 * <ol>
 *   <li>constructor: dependencies are passed in (none here);
 *   <li>{@code @PostConstruct}: every dependency is injected, so initialisation code goes here;
 *   <li>{@link ApplicationReadyEvent}: the whole context has started and Tomcat accepts requests;
 *   <li>{@code @PreDestroy}: the context is closing (Ctrl+C, end of a test).
 * </ol>
 */
@Component
public class LifecycleLogger {

    private static final Logger log = LoggerFactory.getLogger(LifecycleLogger.class);

    @PostConstruct
    void beansReady() {
        log.info("Lifecycle: beans ready");
    }

    @EventListener
    void applicationReady(ApplicationReadyEvent event) {
        // Spring Boot publishes the port Tomcat actually bound (useful with server.port=0).
        String port = event.getApplicationContext().getEnvironment().getProperty("local.server.port", "none");
        log.info("Library ready on port {}", port);
    }

    @PreDestroy
    void shuttingDown() {
        log.info("Lifecycle: shutting down");
    }
}
