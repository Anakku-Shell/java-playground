package dev.playground.library;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.kafka.KafkaContainer;

/**
 * A throwaway Kafka broker, for the tests that switch Kafka on ({@code LoanKafkaIT}): {@code @Import}
 * it next to {@link TestcontainersConfiguration}. Like the PostgreSQL one, {@code @ServiceConnection}
 * turns the container into a {@code KafkaConnectionDetails} bean, which Boot prefers over
 * {@code spring.kafka.bootstrap-servers}. Kept apart so the other contexts
 * do not start a broker they never use. The native build of the broker in {@code compose.yaml}: it
 * starts in about a second (Apache marks it as not for production). Guide: §5.10 Kafka.
 */
@TestConfiguration(proxyBeanMethods = false)
public class KafkaTestcontainersConfiguration {

    @Bean
    @ServiceConnection
    KafkaContainer kafka() {
        return new KafkaContainer("apache/kafka-native:4.3.1");
    }
}
