package dev.playground.library.security;

import dev.playground.library.config.ClockConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

/**
 * Signing and checking JWTs with one shared secret (HMAC-SHA256). This application both issues the
 * tokens ({@link TokenService}) and accepts them (the resource server), so a symmetric key is
 * enough. With a separate identity provider (Keycloak, Entra ID...), the API would hold only the
 * provider's public keys (RS256) and no encoder at all. Guide: §5.7 Security.
 */
@Configuration(proxyBeanMethods = false)
// Registers its settings and the Clock itself, so a slice test can @Import it with nothing else.
@EnableConfigurationProperties(LibrarySecurityProperties.class)
@Import(ClockConfig.class)
public class JwtConfig {

    /** The {@code iss} claim: who issued the token. The decoder rejects any other issuer. */
    public static final String ISSUER = "library";

    @Bean
    SecretKey jwtSigningKey(LibrarySecurityProperties properties) {
        return new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    /**
     * Used by the resource server on every request that carries a bearer token: it checks the
     * signature, then the validators. Defining this bean also switches Boot's own decoder
     * configuration off.
     */
    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSigningKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // exp and nbf, checked against the application's Clock (so a test can move time) with the
        // default 60 seconds of allowed clock skew; then the issuer.
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER)));
        return decoder;
    }

    /**
     * Turns a valid token into an {@code Authentication}. By default the authorities come from the
     * {@code scope} claim as {@code SCOPE_*}; ours come from {@code roles} as {@code ROLE_*}, so
     * {@code hasRole('LIBRARIAN')} works. The authentication's name is the subject: the member id.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
