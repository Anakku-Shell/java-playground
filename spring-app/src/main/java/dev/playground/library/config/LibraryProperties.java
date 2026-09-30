package dev.playground.library.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Typed settings under the {@code library} prefix. Guide: §5.1 Spring Boot fundamentals.
 *
 * <p>A record binds through its constructor, so the settings are immutable once the application
 * has started. {@code @ConfigurationPropertiesScan} on {@code LibraryApplication} registers it as a
 * bean; inject it like any other dependency. Prefer this over scattering {@code @Value("${...}")}
 * strings: one place, type-checked, with defaults and (later) validation.
 *
 * @param name display name of the library ({@code library.name})
 * @param loans loan rules ({@code library.loans.*}); the empty {@code @DefaultValue} creates it
 *     with its own defaults when the whole group is missing, instead of leaving it null
 */
@ConfigurationProperties("library")
public record LibraryProperties(String name, @DefaultValue Loans loans) {

    /**
     * @param maxActive loans a member may hold at once ({@code library.loans.max-active})
     * @param durationDays days until a loan is due ({@code library.loans.duration-days})
     */
    public record Loans(
            @DefaultValue("3") int maxActive,
            @DefaultValue("14") int durationDays) {}
}
