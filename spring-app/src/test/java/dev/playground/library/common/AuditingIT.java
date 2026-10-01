package dev.playground.library.common;

import static dev.playground.library.testing.TestDataFactory.herbert;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.author.Author;
import dev.playground.library.book.Book;
import dev.playground.library.config.JpaAuditingConfig;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Spring Data fills {@code createdAt}/{@code updatedAt} when Hibernate inserts or updates an
 * entity. The time comes from the {@code Clock} bean, replaced here by a mock so the test decides
 * what "now" is. {@code @DataJpaTest} does not load our {@code @Configuration} classes, hence the
 * {@code @Import} of the auditing configuration. Guide: §5.5 Advanced JPA.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class AuditingIT {

    private static final Instant CREATED = Instant.parse("2026-01-10T09:00:00Z");
    private static final Instant EDITED = Instant.parse("2026-02-20T17:30:00Z");

    @MockitoBean
    private Clock clock;

    @Autowired
    private TestEntityManager em;

    @Test
    void insertSetsBothTimestamps() {
        given(clock.instant()).willReturn(CREATED);

        Author author = em.persistFlushFind(new Author("Mary Beard", 1955));

        assertThat(author.getCreatedAt()).isEqualTo(CREATED);
        assertThat(author.getUpdatedAt()).isEqualTo(CREATED);
    }

    @Test
    void updateMovesOnlyUpdatedAt() {
        given(clock.instant()).willReturn(CREATED);
        Author author = em.persistAndFlush(new Author("Mary Berd", 1955));

        given(clock.instant()).willReturn(EDITED);
        author.setName("Mary Beard");
        em.flush(); // dirty checking sends the UPDATE; the listener runs just before it
        em.clear();

        Author reloaded = em.find(Author.class, author.getId());
        assertThat(reloaded.getCreatedAt()).isEqualTo(CREATED);
        assertThat(reloaded.getUpdatedAt()).isEqualTo(EDITED);
    }

    @Test
    void changingOnlyTheAuthorsOfABookIsAnUpdateOfTheBook() {
        given(clock.instant()).willReturn(CREATED);
        Author herbert = em.persist(herbert());
        Book dune = em.persistAndFlush(new Book("9780441013593", "Dune", 1965, 1));

        given(clock.instant()).willReturn(EDITED);
        dune.replaceAuthors(Set.of(herbert));
        em.flush(); // an INSERT into book_authors, and an UPDATE of the book itself:
        em.clear();

        // Book has a @Version (§5.6), and a change to a collection it owns counts as a change of the
        // book. Hibernate bumps the version, and the listener sets updatedAt on that UPDATE. Without
        // the version, no UPDATE of the row would be sent and updatedAt would stay at CREATED.
        Object[] row = (Object[]) em.getEntityManager()
                .createNativeQuery("select version, updated_at from books where id = ?1")
                .setParameter(1, dune.getId())
                .getSingleResult();
        assertThat(row[0]).isEqualTo(1L);
        assertThat(row[1]).isEqualTo(EDITED);
    }
}
