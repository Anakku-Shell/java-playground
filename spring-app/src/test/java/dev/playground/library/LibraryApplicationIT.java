package dev.playground.library;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * Starts the whole ApplicationContext once, on a throwaway PostgreSQL. If any bean or configuration
 * is broken, this is the test that fails first. Starting at all proves more than it seems: Flyway
 * migrated the empty database, and Hibernate's {@code ddl-auto: validate} found every entity's
 * table and columns. Guide: §5.1, §5.4.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LibraryApplicationIT {

    @Autowired
    private Flyway flyway;

    @Test
    void contextLoadsOnAMigratedDatabase() {
        // flyway_schema_history, the table where Flyway records what it applied.
        assertThat(flyway.info().applied())
                .extracting(MigrationInfo::getScript)
                .containsExactly("V1__create_authors_and_books.sql");
    }
}
