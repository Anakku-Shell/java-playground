package dev.playground.library.common;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * One page of a list, as the API returns it. Spring's {@code Page} is not returned directly: its
 * JSON is an implementation detail that Spring Data itself warns about, and this record is a
 * contract we control. {@code page} is zero-based, like the {@code ?page=} parameter.
 * Guide: §5.5 Advanced JPA.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
