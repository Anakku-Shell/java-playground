package dev.playground.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the library API. Guide: §3 How a Spring Boot app runs.
 *
 * <p>{@code @SpringBootApplication} = {@code @SpringBootConfiguration} + {@code @EnableAutoConfiguration} +
 * {@code @ComponentScan} of this package and its sub-packages. That is why every feature package
 * ({@code book}, {@code loan}...) lives under {@code dev.playground.library}.
 *
 * <p>{@code @ConfigurationPropertiesScan} does the same for {@code @ConfigurationProperties}
 * records such as {@code config.LibraryProperties} (§5.1).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LibraryApplication {

    public static void main(String[] args) {
        // Builds the ApplicationContext, runs auto-configuration and starts embedded Tomcat.
        SpringApplication.run(LibraryApplication.class, args);
    }
}
