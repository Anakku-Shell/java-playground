package dev.playground.library;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Empties every table between full-stack tests, which share one database and commit for real. One
 * {@code TRUNCATE} for all of them: deleting table by table would have to follow the foreign keys
 * (loans before books and members, book_authors before books and authors). {@code RESTART IDENTITY}
 * resets the id sequences too.
 *
 * <p>The alternative, {@code @DirtiesContext}, throws the whole Spring context away after a test,
 * and the next test pays for a new one (and here a new PostgreSQL container): seconds instead of
 * milliseconds. Keep it for a test that really changes the context itself, not its data.
 * Guide: §5.5 Advanced JPA, §5.8 Testing.
 */
public final class TestTables {

    private TestTables() {}

    public static void truncateAll(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE loans, book_authors, members, books, authors, audit_events RESTART IDENTITY");
    }
}
