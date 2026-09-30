package dev.playground.library.common;

import java.util.Set;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Helpers for paged queries. Guide: §5.5 Advanced JPA. */
public final class Paging {

    private Paging() {}

    /**
     * Checks the requested sort against the properties an endpoint allows, then adds {@code id} as
     * the last sort key.
     *
     * <p>Why an allow-list: {@code ?sort=authors.name} names a real path, so Spring Data accepts it,
     * but it joins a collection. A book with two authors becomes two rows, and {@code LIMIT/OFFSET}
     * counts rows, not books: duplicates across pages and a wrong total, with a 200.
     *
     * <p>Why the id: sorting by title alone leaves equal titles in whatever order the database
     * likes, and that order may differ between the queries for page 1 and page 2, so a row could
     * show up twice or never. The id makes the order total.
     */
    public static Pageable sanitize(Pageable pageable, Set<String> sortable) {
        for (Sort.Order order : pageable.getSort()) {
            if (!sortable.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), new TreeSet<>(sortable));
            }
        }
        return PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort().and(Sort.by("id")));
    }
}
