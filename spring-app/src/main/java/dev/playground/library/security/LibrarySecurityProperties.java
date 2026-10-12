package dev.playground.library.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Security settings under {@code library.security}. The JWT secret has no default on purpose: the
 * dev profile sets a dev-only value, the tests set their own, and any other run must provide one
 * (for example the environment variable {@code LIBRARY_SECURITY_JWT_SECRET}, which relaxed binding
 * maps to {@code library.security.jwt.secret}). Without it the application stops at startup,
 * naming the key. Guide: §5.7 Security.
 *
 * @param jwt how tokens are signed and how long they live
 * @param cors which browser origins may call the API
 */
@ConfigurationProperties("library.security")
@Validated
public record LibrarySecurityProperties(
        @Valid @DefaultValue Jwt jwt, @Valid @DefaultValue Cors cors) {

    /**
     * @param secret the HMAC key ({@code library.security.jwt.secret}). HS256 needs at least 256
     *     bits: 32 bytes, so 32 ASCII characters
     * @param ttl how long a token is valid ({@code library.security.jwt.ttl}, e.g. {@code 1h})
     */
    public record Jwt(
            @NotBlank @Size(min = 32) String secret,
            @DefaultValue("1h") Duration ttl) {}

    /**
     * @param allowedOrigins origins allowed to call {@code /api/**} from a browser
     *     ({@code library.security.cors.allowed-origins}); by default the Angular dev server
     */
    public record Cors(
            @DefaultValue("http://localhost:4200") @NotEmpty List<String> allowedOrigins) {}
}
