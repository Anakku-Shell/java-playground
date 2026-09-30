package dev.playground.library.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Checks an email and password at login. A {@code DaoAuthenticationProvider} loads the user with
 * the {@code UserDetailsService} and compares the password with the {@code PasswordEncoder}. For an
 * unknown email it still hashes a dummy password, so that login takes as long as one with a wrong
 * password: the timing does not tell which emails exist. Kept apart from {@link SecurityConfig} so
 * slice tests can import the filter chain without a repository. Guide: §5.7 Security.
 */
@Configuration(proxyBeanMethods = false)
public class AuthenticationConfig {

    @Bean
    AuthenticationManager authenticationManager(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
