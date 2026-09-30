package dev.playground.library;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import dev.playground.library.book.BookRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * The dev profile's sample data (db/demo/R__demo_data.sql) is valid SQL for the current schema, and
 * running it again adds nothing. Guide: §5.4 Persistence with JPA.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestcontainersConfiguration.class)
class DemoDataIT {

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private BookRepository books;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PostgreSQLContainer postgres;

    @Test
    void devProfileLoadsTheDemoData() {
        // Ids in the script's order, so .http files can rely on "author 1 is Frank Herbert".
        assertThat(authors.findAll(Sort.by("id")))
                .extracting(Author::getName)
                .containsExactly("Frank Herbert", "Ursula K. Le Guin", "Jane Austen", "Carl Sagan", "Mary Beard");
        assertThat(books.count()).isEqualTo(10);
    }

    @Test
    void aRunWithoutTheDevProfileStillStartsOnThisDatabase() {
        // This database has R__demo_data in flyway_schema_history, and a plain run does not have
        // db/demo on its path. By default Flyway then refuses to start ("applied migration not
        // resolved locally"). application.yml tells it to ignore a missing repeatable migration.
        try (ConfigurableApplicationContext plainRun = new SpringApplicationBuilder(LibraryApplication.class)
                .web(WebApplicationType.NONE)
                .properties(
                        "spring.datasource.url=" + postgres.getJdbcUrl(),
                        "spring.datasource.username=" + postgres.getUsername(),
                        "spring.datasource.password=" + postgres.getPassword(),
                        "spring.docker.compose.enabled=false")
                .run()) {
            assertThat(plainRun.getBean(BookRepository.class).count()).isEqualTo(10);
        }
    }

    @Test
    void runningTheDemoScriptAgainAddsNothing() {
        // What Flyway does when the file changes: it runs the whole script again over the existing rows.
        new ResourceDatabasePopulator(new ClassPathResource("db/demo/R__demo_data.sql")).execute(dataSource);

        assertThat(authors.count()).isEqualTo(5);
        assertThat(books.count()).isEqualTo(10);
    }
}
