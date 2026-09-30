package dev.playground.library.member.dto;

/** What the API returns for a member. The role arrives with security (§5.7). Guide: §5.5. */
public record MemberResponse(Long id, String email, String fullName) {}
