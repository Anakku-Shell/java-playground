package dev.playground.library.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * The Kafka side of the application, switched on by {@code library.kafka.enabled=true} (the dev
 * profile). Off, none of the {@code messaging} beans exist, so the tests and runs without a broker
 * never try to reach one. Boot's own Kafka beans ({@code KafkaTemplate}, the listener container
 * factory) are still created, but connect only when used.
 *
 * <p>The serializers, the consumer group's starting offset and the producer's timeouts are in
 * {@code application.yml} ({@code spring.kafka.*}). Guide: §5.10 Kafka.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("library.kafka.enabled")
public class KafkaConfig {

    public static final String LOANS_TOPIC = "library.loans";

    /**
     * Boot's {@code KafkaAdmin} creates the topics declared as beans at startup, if missing. Three
     * partitions: up to three consumers of one group can share the work. Ordering holds within a
     * partition only, so the key (the book id) decides what stays in order: the loans of one book.
     * One replica: a single local broker has nowhere to copy to (production uses 3).
     */
    @Bean
    NewTopic loansTopic() {
        return TopicBuilder.name(LOANS_TOPIC).partitions(3).replicas(1).build();
    }
}
