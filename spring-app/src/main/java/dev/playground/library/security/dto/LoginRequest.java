package dev.playground.library.security.dto;

import jakarta.validation.constraints.NotBlank;

/** Body of {@code POST /api/auth/login}. Guide: §5.7 Security. */
public record LoginRequest(@NotBlank String email, @NotBlank String password) {

    /** Keeps the password out of logs (a record's own toString would print it). */
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}
