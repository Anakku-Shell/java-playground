package dev.playground.library.author.dto;

/**
 * Body of {@code POST /api/authors}. {@code Integer} rather than {@code int}: the birth year is
 * optional, and a missing JSON field becomes {@code null}. Guide: §5.2 REST API.
 */
public record CreateAuthorRequest(String name, Integer birthYear) {}
