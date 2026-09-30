package dev.playground.library.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/members}. {@code @Email} checks the shape only (it accepts
 * {@code a@b}); whether the address exists is not something validation can know. Guide: §5.5.
 */
public record CreateMemberRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(max = 200) String fullName) {}
