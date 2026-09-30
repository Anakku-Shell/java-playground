package dev.playground.library.member.dto;

import dev.playground.library.member.Role;

/**
 * What the API returns for a member: never the password hash. Guide: §5.5 Advanced JPA, §5.7
 * Security.
 */
public record MemberResponse(Long id, String email, String fullName, Role role) {}
