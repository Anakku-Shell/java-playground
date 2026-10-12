package dev.playground.library.config;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on Spring Data auditing: entities with {@code @EntityListeners(AuditingEntityListener.class)}
 * get their {@code @CreatedDate}/{@code @LastModifiedDate} fields set on insert and update. The time
 * comes from our {@code Clock} bean instead of the system clock, so tests control it.
 *
 * <p>A class of its own, not an annotation on {@code LibraryApplication}: {@code @WebMvcTest} loads
 * the main class's annotations but no JPA, and would fail to start. {@code @DataJpaTest} does not
 * load {@code @Configuration} classes, so JPA tests {@code @Import} this one. Guide: §5.5.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
@Import(ClockConfig.class)
public class JpaAuditingConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
