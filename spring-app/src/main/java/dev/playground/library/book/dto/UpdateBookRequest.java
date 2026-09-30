package dev.playground.library.book.dto;

import dev.playground.library.book.validation.ValidIsbn;
import dev.playground.library.common.validation.PastOrPresentYear;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Body of {@code PUT /api/books/{id}}: a full replacement, same rules as the create request. Guide: §5.2, §5.3. */
public record UpdateBookRequest(
        @NotBlank @ValidIsbn String isbn,
        @NotBlank @Size(max = 300) String title,
        @PastOrPresentYear Integer publishedYear,
        @PositiveOrZero int totalCopies,
        Set<Long> authorIds) {}
