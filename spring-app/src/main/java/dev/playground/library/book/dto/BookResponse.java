package dev.playground.library.book.dto;

import java.util.List;

/**
 * What the API returns for a book. {@code availableCopies} is computed, not stored: total copies
 * minus active loans. {@code authors} is sorted by name. Guide: §5.2 REST API, §5.5 Advanced JPA.
 */
public record BookResponse(
        Long id,
        String isbn,
        String title,
        Integer publishedYear,
        int totalCopies,
        int availableCopies,
        List<AuthorSummary> authors) {}
