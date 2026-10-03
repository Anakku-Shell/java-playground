package dev.playground.library.legacy;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * LEGACY EXAMPLE: the DTO style of a Java 8 / Boot 2 codebase. {@code @Data} generates getters, setters,
 * {@code equals}/{@code hashCode} over every field, and {@code toString}; {@code @Builder} a fluent builder
 * ({@code LegacyBookDto.builder().title("Dune").build()}); the two constructor annotations write the no-argument
 * and all-arguments constructors that frameworks and the builder need. This project writes a record instead
 * ({@code BookResponse}). Test scope only: main code has no Lombok. Guide: §6 Legacy.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LegacyBookDto {

    private String isbn;
    private String title;
    private Integer publishedYear;
}
