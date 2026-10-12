package dev.playground.library;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A throwaway PostgreSQL for the tests that need a database: {@code @Import} this class. Spring
 * starts the container as a bean, and {@code @ServiceConnection} makes Boot build the DataSource
 * from it (URL, user, password), with no properties. Flyway then migrates the empty database, so
 * every test context gets the real schema. The same image as {@code compose.yaml}. Docker must be
 * running. Guide: §5.4 Persistence with JPA.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:17");
    }
}
