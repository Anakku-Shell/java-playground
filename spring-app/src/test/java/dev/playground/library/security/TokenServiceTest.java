package dev.playground.library.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.member.Role;
import dev.playground.library.security.dto.TokenResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidationException;

/**
 * Issuing and checking tokens, with no Spring context: the beans of {@link JwtConfig} built by
 * hand, and fixed clocks, so "expired" is an exact moment. Guide: §5.7 Security.
 */
class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-30T10:00:00Z");
    private static final MemberUserDetails ADA =
            new MemberUserDetails(7L, "ada@library.test", "{noop}irrelevant", Role.MEMBER);

    private final JwtConfig config = new JwtConfig();
    private final SecretKey key = config.jwtSigningKey(properties("a-test-secret-of-at-least-32-bytes!!"));
    private final TokenService tokens = new TokenService(
            config.jwtEncoder(key),
            properties("a-test-secret-of-at-least-32-bytes!!"),
            Clock.fixed(NOW, ZoneOffset.UTC));

    private static LibrarySecurityProperties properties(String secret) {
        return new LibrarySecurityProperties(
                new LibrarySecurityProperties.Jwt(secret, Duration.ofHours(1)),
                new LibrarySecurityProperties.Cors(List.of("http://localhost:4200")));
    }

    /** A decoder whose "now" is {@code at}: it checks the expiry against that moment. */
    private JwtDecoder decoderAt(Instant at) {
        return config.jwtDecoder(key, Clock.fixed(at, ZoneOffset.UTC));
    }

    @Test
    void theTokenCarriesTheMemberAndExpiresInOneHour() {
        TokenResponse response = tokens.issue(ADA);

        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600);
        Jwt jwt = decoderAt(NOW).decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("ada@library.test");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("MEMBER");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("library");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
        // Signed, not encrypted: anyone holding the token can read these claims (base64). Never put
        // a secret in a JWT.
        assertThat(jwt.getHeaders()).containsEntry("alg", "HS256");
    }

    @Test
    void anExpiredTokenIsRejected() {
        String token = tokens.issue(ADA).accessToken();

        // Validators allow 60 seconds of clock skew, so one minute after expiry still passes.
        assertThat(decoderAt(NOW.plus(Duration.ofMinutes(61))).decode(token)).isNotNull();
        assertThatThrownBy(() -> decoderAt(NOW.plus(Duration.ofMinutes(62))).decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void aTamperedTokenIsRejected() {
        String token = tokens.issue(ADA).accessToken();
        // header.payload.signature: swap the payload for one that claims to be member 1, a librarian.
        String[] parts = token.split("\\.");
        String forged = Base64.getUrlEncoder().withoutPadding().encodeToString("""
                        {"sub":"1","roles":["LIBRARIAN"],"iss":"library","exp":4102444800}""".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> decoderAt(NOW).decode(parts[0] + "." + forged + "." + parts[2]))
                .isInstanceOf(BadJwtException.class)
                .hasMessageContaining("Invalid signature");
    }

    @Test
    void aTokenFromAnotherIssuerIsRejected() {
        // Right key, right dates, wrong "iss": the issuer validator refuses it.
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("someone-else")
                .subject("7")
                .issuedAt(NOW)
                .expiresAt(NOW.plus(Duration.ofHours(1)))
                .build();
        String token = config.jwtEncoder(key)
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();

        assertThatThrownBy(() -> decoderAt(NOW).decode(token))
                .isInstanceOf(JwtValidationException.class)
                .hasMessageContaining("iss");
    }

    @Test
    void aTokenSignedWithAnotherKeyIsRejected() {
        SecretKey otherKey = config.jwtSigningKey(properties("another-secret-of-at-least-32-bytes"));
        TokenService otherIssuer = new TokenService(
                config.jwtEncoder(otherKey),
                properties("another-secret-of-at-least-32-bytes"),
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> decoderAt(NOW).decode(otherIssuer.issue(ADA).accessToken()))
                .isInstanceOf(BadJwtException.class);
    }
}
