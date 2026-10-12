package dev.playground.library.security.dto;

/**
 * The body of a successful login, shaped like an OAuth2 token response. The client sends
 * {@code accessToken} back on each request as {@code Authorization: Bearer <accessToken>}.
 * Guide: §5.7 Security.
 *
 * @param accessToken the signed JWT
 * @param tokenType always {@code Bearer}
 * @param expiresIn seconds until the token expires
 */
public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
