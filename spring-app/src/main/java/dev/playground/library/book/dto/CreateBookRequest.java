package dev.playground.library.book.dto;

import dev.playground.library.book.validation.ValidIsbn;
import dev.playground.library.common.validation.PastOrPresentYear;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * Body of {@code POST /api/books}. {@code authorIds} is optional (missing = no authors); the
 * constraints inside the type argument check each element (container element constraints), and an
 * id with no author is a 404 from the service (§5.5). {@code int totalCopies} is a primitive: Jackson 3 rejects a missing or null value
 * with a 400 while reading the body, before validation runs (Jackson 2 used 0). The ISBN may be
 * ISBN-10 or ISBN-13, with or without hyphens; it is stored as 13 digits. Guide: §5.2, §5.3.
 */
public record CreateBookRequest(
        @NotBlank @ValidIsbn String isbn,
        @NotBlank @Size(max = 300) String title,
        @PastOrPresentYear Integer publishedYear,
        @PositiveOrZero int totalCopies,
        Set<@NotNull @Positive Long> authorIds) {}
