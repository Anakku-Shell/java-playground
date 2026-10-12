package dev.playground.library.book;

/**
 * An interface projection: a repository method that returns it selects only these columns, and
 * Spring Data backs each getter with a value from the row. Read-only, and cheaper than an entity.
 * Used by {@code GET /api/authors/{id}/books}. Guide: §5.5 Advanced JPA.
 */
public interface BookTitleOnly {

    Long getId();

    String getTitle();
}
