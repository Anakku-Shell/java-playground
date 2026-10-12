package dev.playground.library.author.dto;

/** One entry of {@code GET /api/authors/{id}/books}: just enough to link to the book. Guide: §5.5. */
public record AuthorBookResponse(Long id, String title) {}
