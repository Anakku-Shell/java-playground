package dev.playground.library;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Empties every table between full-stack tests, which share one database and commit for real. One
 * {@code TRUNCATE} for all of them: deleting table by table would have to follow the foreign keys
 * (loans before books and members, book_authors before books and authors). {@code RESTART IDENTITY}
 * resets the id sequences too. Guide: §5.5 Advanced JPA.
 */
public final class TestTables {

    private TestTables() {}

    public static void truncateAll(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE loans, book_authors, members, books, authors RESTART IDENTITY");
    }
}
