package dev.playground.library.security;

import dev.playground.library.member.MemberService;
import dev.playground.library.member.dto.MemberResponse;
import dev.playground.library.member.dto.RegisterRequest;
import dev.playground.library.security.dto.LoginRequest;
import dev.playground.library.security.dto.TokenResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Registration and login, the two public endpoints of the API (SecurityConfig permits
 * {@code /api/auth/**}). Guide: §5.7 Security.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MemberService members;
    private final AuthenticationManager authenticationManager;
    private final TokenService tokens;

    public AuthController(MemberService members, AuthenticationManager authenticationManager, TokenService tokens) {
        this.members = members;
        this.authenticationManager = authenticationManager;
        this.tokens = tokens;
    }

    @PostMapping("/register")
    public ResponseEntity<MemberResponse> register(@Valid @RequestBody RegisterRequest request) {
        MemberResponse created = members.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/members/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Checks the password and answers with a token. A wrong email or password makes
     * {@code authenticate} throw {@code BadCredentialsException}, which GlobalExceptionHandler turns
     * into a 401.
     */
    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));
        return tokens.issue((MemberUserDetails) authentication.getPrincipal());
    }
}
