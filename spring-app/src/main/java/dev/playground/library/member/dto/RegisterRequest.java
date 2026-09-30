package dev.playground.library.member.dto;

import dev.playground.library.common.validation.MaxBytes;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/auth/register}. {@code @Email} checks the shape only (it accepts
 * {@code a@b}). The password: at least 8 characters, and at most 72 bytes because BCrypt reads no
 * more ({@link MaxBytes}). Guide: §5.5 Advanced JPA, §5.7 Security.
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String fullName,

        @NotBlank @Size(min = 8, message = "must be at least 8 characters") @MaxBytes(72)
        String password) {

    /** A record's own toString lists every component: this one keeps the password out of logs. */
    @Override
    public String toString() {
        return "RegisterRequest[email=" + email + ", fullName=" + fullName + ", password=***]";
    }
}
