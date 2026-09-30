package dev.playground.library.security;

import dev.playground.library.security.dto.TokenResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues the access token after a successful login. The token is the whole session: the server
 * keeps nothing, and each request proves who sent it by carrying a token only this server could
 * have signed. The price: a token stays valid until it expires, even after a role change, hence the
 * short lifetime. Guide: §5.7 Security.
 */
@Service
public class TokenService {

    private final JwtEncoder encoder;
    private final Duration ttl;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, LibrarySecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.ttl = properties.jwt().ttl();
        this.clock = clock;
    }

    public TokenResponse issue(MemberUserDetails member) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtConfig.ISSUER)
                .subject(member.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("email", member.email())
                .claim("roles", List.of(member.role().name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenResponse(token, "Bearer", ttl.toSeconds());
    }
}
