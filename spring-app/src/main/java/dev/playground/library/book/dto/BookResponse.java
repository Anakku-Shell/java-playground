package dev.playground.library.book.dto;

import java.util.List;

/**
 * What the API returns for a book. {@code availableCopies} and {@code authors} are computed, not
 * stored: until loans and the book–author relation exist (§5.5) they are {@code totalCopies} and
 * an empty list. Guide: §5.2 REST API.
 */
public record BookResponse(
        Long id,
        String isbn,
        String title,
        Integer publishedYear,
        int totalCopies,
        int availableCopies,
        List<AuthorSummary> authors) {}
