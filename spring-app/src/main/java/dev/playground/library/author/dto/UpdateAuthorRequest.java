package dev.playground.library.author.dto;

/**
 * Body of {@code PUT /api/authors/{id}}: a full replacement of the editable fields. A separate
 * record from the create request, because the two usually drift apart. Guide: §5.2 REST API.
 */
public record UpdateAuthorRequest(String name, Integer birthYear) {}
