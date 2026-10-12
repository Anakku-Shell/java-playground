package dev.playground.library;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import dev.playground.library.member.Member;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * Callers for full-stack tests that are not about security itself. {@code jwt()} puts an
 * already-authenticated token in the request, as the resource server would after checking a real
 * one: no signing, no decoder, and it works from any thread (unlike {@code @WithMockUser}, whose
 * user lives in the test thread only). The security tests themselves send real tokens
 * ({@code JwtAuthIT}, {@code LoanSecurityIT}). Guide: §5.7 Security.
 */
public final class TestUsers {

    /** A librarian who is not a member row: enough for anything but borrowing for themselves. */
    private static final String LIBRARIAN_ID = "999";

    private TestUsers() {}

    public static JwtRequestPostProcessor librarian() {
        return jwt().jwt(token -> token.subject(LIBRARIAN_ID))
                .authorities(new SimpleGrantedAuthority("ROLE_LIBRARIAN"));
    }

    public static JwtRequestPostProcessor member(Member member) {
        return jwt().jwt(token -> token.subject(member.getId().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }
}
