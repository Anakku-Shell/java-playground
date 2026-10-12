package dev.playground.library.author.dto;

import dev.playground.library.common.validation.PastOrPresentYear;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/authors/{id}}: a full replacement of the editable fields. A separate
 * record from the create request, because the two usually drift apart. Guide: §5.2 REST API, §5.3.
 */
public record UpdateAuthorRequest(
        @NotBlank @Size(max = 200) String name,
        @PastOrPresentYear Integer birthYear) {}
