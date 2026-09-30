package dev.playground.library.book.dto;

import java.util.Set;

/** Body of {@code PUT /api/books/{id}}: a full replacement. Guide: §5.2 REST API. */
public record UpdateBookRequest(
        String isbn, String title, Integer publishedYear, int totalCopies, Set<Long> authorIds) {}
