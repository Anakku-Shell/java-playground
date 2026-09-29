package dev.playground.library;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the library API. Guide: §3 How a Spring Boot app runs.
 *
 * <p>{@code @SpringBootApplication} = {@code @SpringBootConfiguration} + {@code @EnableAutoConfiguration} +
 * {@code @ComponentScan} of this package and its sub-packages. That is why every feature package
 * ({@code book}, {@code loan}...) lives under {@code dev.playground.library}.
 */
@SpringBootApplication
public class LibraryApplication {

    public static void main(String[] args) {
        // Builds the ApplicationContext, runs auto-configuration and starts embedded Tomcat.
        SpringApplication.run(LibraryApplication.class, args);
    }
}
