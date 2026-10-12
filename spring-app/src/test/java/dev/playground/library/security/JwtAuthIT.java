package dev.playground.library.security;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.member.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

/**
 * The bearer token through the whole filter chain: real tokens, the real decoder, the real 401s.
 * Guide: §5.7 Security.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JwtAuthIT {

    private static final MemberUserDetails ADA =
            new MemberUserDetails(1L, "ada@library.test", "{noop}unused", Role.MEMBER);

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private TokenService tokens;

    @Autowired
    private JwtEncoder encoder;

    @Autowired
    private LibrarySecurityProperties properties;

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void aValidTokenIsAccepted() {
        assertThat(mvc.get()
                        .uri("/api/books")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(tokens.issue(ADA).accessToken())))
                .hasStatusOk();
    }

    @Test
    void noTokenReturns401WithAChallenge() {
        assertThat(mvc.get().uri("/api/books"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyJson()
                .isStrictlyEqualTo("""
                        {
                          "title": "Unauthorized",
                          "status": 401,
                          "detail": "Authentication is required: send a bearer token.",
                          "instance": "/api/books"
                        }
                        """);
    }

    @Test
    void tamperedTokenReturns401() {
        // Change one character of the payload: the signature no longer matches it.
        String token = tokens.issue(ADA).accessToken();
        int payload = token.indexOf('.') + 5;
        String tampered =
                token.substring(0, payload) + (token.charAt(payload) == 'A' ? 'B' : 'A') + token.substring(payload + 1);

        assertThat(mvc.get().uri("/api/books").header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("The bearer token is invalid or has expired.");
    }

    @Test
    void expiredTokenReturns401() {
        // Issued two hours ago with a one-hour lifetime: expired an hour ago, well past the skew.
        TokenService yesterday = new TokenService(
                encoder, properties, Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC));

        assertThat(mvc.get()
                        .uri("/api/books")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                bearer(yesterday.issue(ADA).accessToken())))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
    }

    @Test
    void somethingThatIsNotAJwtReturns401() {
        assertThat(mvc.get().uri("/api/books").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aMalformedBearerHeaderIsAnInvalidTokenToo() {
        // Rejected before the decoder: the header does not have the shape "Bearer <token>". The
        // client did send a token (an interceptor adding "Bearer null", say), so say it is invalid.
        assertThat(mvc.get().uri("/api/books").header(HttpHeaders.AUTHORIZATION, "Bearer a b"))
                .hasStatus(HttpStatus.UNAUTHORIZED)
                .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"")
                .bodyJson()
                .extractingPath("$.detail")
                .isEqualTo("The bearer token is invalid or has expired.");
    }

    @Test
    void publicEndpointsNeedNoToken() {
        assertThat(mvc.get().uri("/api/info")).hasStatusOk();
        assertThat(mvc.get().uri("/v3/api-docs")).hasStatusOk();
    }

    @Test
    void aBadTokenIsRejectedEvenOnAPublicEndpoint() {
        // The resource server checks any bearer token it sees before the URL rules run. A client
        // sending a broken token has a bug worth hearing about, even where no token is needed.
        assertThat(mvc.get().uri("/api/info").header(HttpHeaders.AUTHORIZATION, bearer("not-a-jwt")))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
