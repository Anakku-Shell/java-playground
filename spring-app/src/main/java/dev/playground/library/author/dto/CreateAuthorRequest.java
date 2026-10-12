package dev.playground.library.author.dto;

import dev.playground.library.common.validation.PastOrPresentYear;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/authors}. {@code Integer} rather than {@code int}: the birth year is
 * optional, and a missing JSON field becomes {@code null}. The constraints are checked when the
 * controller parameter has {@code @Valid}. Guide: §5.2 REST API, §5.3 Validation & errors.
 */
public record CreateAuthorRequest(
        @NotBlank @Size(max = 200) String name,
        @PastOrPresentYear Integer birthYear) {}
