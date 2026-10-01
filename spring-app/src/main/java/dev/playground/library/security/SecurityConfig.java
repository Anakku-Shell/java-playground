package dev.playground.library.security;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Who may call what. Every request goes through the {@link SecurityFilterChain} below before it
 * reaches a controller: CORS, then the bearer token (the OAuth2 resource server), then the URL
 * rules. The rules here are coarse (by role and path); rules that need data, such as "only the
 * member who owns the loan", are method security on the service ({@code @PreAuthorize}), switched
 * on by {@code @EnableMethodSecurity}. Guide: §5.7 Security.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * Only in a servlet application: a run without a web server (a batch job, some tests) has no
     * {@code HttpSecurity} to build a filter chain with. Method security still applies there.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver)
            throws Exception {
        SecurityProblemHandler problems = new SecurityProblemHandler(resolver);
        http
                // CSRF protection defends cookie-based sessions: a browser sends cookies with any request,
                // even one a malicious page triggers. A bearer token is never sent automatically: the
                // client adds the header itself. With no session cookie there is nothing to forge.
                .csrf(csrf -> csrf.disable())
                // Uses the CorsConfigurationSource bean below.
                .cors(Customizer.withDefaults())
                // No HttpSession: each request authenticates with its own token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Checked top to bottom; the first matching rule decides.
                .authorizeHttpRequests(auth -> auth
                        // "/**" also matches the path itself: /api/info and /api/info/greetings.
                        .requestMatchers("/api/auth/**", "/api/info/**", "/error")
                        .permitAll()
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**")
                        .permitAll()
                        // Actuator (§5.9): health and info for probes and humans, the rest for librarians.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/actuator/**")
                        .hasRole("LIBRARIAN")
                        // Importing calls Open Library, even the GET preview: librarians only. Before the
                        // GET rule below, which would match it first.
                        .requestMatchers("/api/books/import/**")
                        .hasRole("LIBRARIAN")
                        // The catalogue: anyone logged in reads it, librarians change it.
                        .requestMatchers(HttpMethod.GET, "/api/books/**", "/api/authors/**")
                        .authenticated()
                        .requestMatchers("/api/books/**", "/api/authors/**")
                        .hasRole("LIBRARIAN")
                        // Any member borrows; everyone's loans, the list, is for librarians. A single loan
                        // is checked by its owner, in LoanService. The list rule names no method on purpose:
                        // HEAD runs the GET handler too, and a GET-only rule would let it through.
                        .requestMatchers(HttpMethod.POST, "/api/loans")
                        .authenticated()
                        .requestMatchers("/api/loans", "/api/loans/overdue")
                        .hasRole("LIBRARIAN")
                        .requestMatchers("/api/members/me", "/api/members/me/**")
                        .authenticated()
                        .requestMatchers("/api/members/**", "/api/audit-events/**")
                        .hasRole("LIBRARIAN")
                        // Anything not listed needs a login, at least. (denyAll() would be stricter, but
                        // would also turn a typo in a URL into a 403 instead of a 404.)
                        .anyRequest()
                        .authenticated())
                // Reads "Authorization: Bearer <jwt>", checks it with the JwtDecoder bean and turns it
                // into an Authentication with JwtAuthenticationConverter (roles claim -> ROLE_*).
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problems)
                        .accessDeniedHandler(problems))
                // 401 and 403 as ProblemDetail, like every other error of this API.
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(problems).accessDeniedHandler(problems));
        return http.build();
    }

    /**
     * Stores {@code {bcrypt}$2a$10$...}: the prefix names the algorithm, so a future default (say
     * Argon2) can hash new passwords while old hashes still match. BCrypt is slow on purpose (a
     * cost factor of 10 means 2^10 rounds), which makes guessing expensive.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Browsers block a page on one origin (http://localhost:4200, the Angular dev server) from reading
     * responses from another (http://localhost:8080) unless the server allows it. For anything but a
     * simple GET the browser first sends a "preflight" OPTIONS request asking; this answers it.
     * CORS protects users in browsers only: curl, Postman and other servers ignore it.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource(LibrarySecurityProperties properties) {
        CorsConfiguration api = new CorsConfiguration();
        api.setAllowedOrigins(properties.cors().allowedOrigins());
        api.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        api.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        // Response headers a script may read besides the basic ones: Location after a POST (201).
        api.setExposedHeaders(List.of("Location"));
        // How long the browser may cache a preflight answer.
        api.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", api);
        return source;
    }
}
