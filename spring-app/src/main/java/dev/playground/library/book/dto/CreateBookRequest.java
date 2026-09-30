package dev.playground.library.book.dto;

import java.util.Set;

/**
 * Body of {@code POST /api/books}. {@code authorIds} is accepted but ignored until §5.5 links books
 * to authors. {@code int totalCopies} is a primitive: Jackson 3 rejects a missing or null value
 * with a 400 (Jackson 2 used 0). Guide: §5.2 REST API.
 */
public record CreateBookRequest(
        String isbn, String title, Integer publishedYear, int totalCopies, Set<Long> authorIds) {}
