package dev.playground.library.book;

import static dev.playground.library.testing.TestDataFactory.dune;
import static dev.playground.library.testing.TestDataFactory.duneMessiah;
import static dev.playground.library.testing.TestDataFactory.emma;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.jdbc.Sql;

/**
 * The repository against a real PostgreSQL. {@code @DataJpaTest} is a slice: JPA, Flyway and the
 * repositories, no web layer and no services. Each test runs in a transaction that is rolled back
 * at the end, so the tests do not see each other's rows. Our {@code @Configuration} classes are not
 * part of the slice: without the auditing one, {@code created_at} would be inserted as null (§5.5).
 * Guide: §5.4 Persistence with JPA, §5.8 Testing ({@code @Sql}).
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
class BookRepositoryIT {

    @Autowired
    private BookRepository repository;

    // The EntityManager, with test helpers (persistAndFlush, clear...).
    @Autowired
    private TestEntityManager em;

    private Book dune;
    private Book messiah;

    @BeforeEach
    void saveSomeBooks() {
        dune = repository.save(dune());
        messiah = repository.save(duneMessiah());
        repository.save(emma());
    }

    @Test
    void derivedQueryMatchesPartOfTheTitleIgnoringCase() {
        // findByTitleContainingIgnoreCase -> ... where upper(b.title) like upper(?) (with % around it)
        assertThat(repository.findByTitleContainingIgnoreCase("dUNE", Sort.by("title")))
                .extracting(Book::getTitle)
                .containsExactly("Dune", "Dune Messiah");
    }

    @Test
    void jpqlQueryFiltersAYearRange() {
        assertThat(repository.findPublishedBetween(1960, 1970))
                .extracting(Book::getTitle)
                .containsExactly("Dune", "Dune Messiah");
    }

    @Test
    void nativeQueryUsesPostgresFullTextSearch() {
        // English stemming: "dunes" and "messiahs" match "Dune Messiah". JPQL cannot say this.
        assertThat(repository.searchTitles("dunes messiahs"))
                .extracting(Book::getTitle)
                .containsExactly("Dune Messiah");
    }

    @Test
    void existsQueriesCheckTheIsbnAndCanExcludeTheBookItself() {
        assertThat(repository.existsByIsbn("9780441013593")).isTrue();
        assertThat(repository.existsByIsbn("9780000000002")).isFalse();
        // "Another book has this ISBN": the book itself does not count.
        assertThat(repository.existsByIsbnAndIdNot("9780441013593", dune.getId()))
                .isFalse();
        assertThat(repository.existsByIsbnAndIdNot("9780441013593", messiah.getId()))
                .isTrue();
    }

    @Test
    @Sql("/sql/le-guin-books.sql")
    void anSqlScriptAddsRowsForOneTest() {
        // 3 books from @BeforeEach plus the 2 of the script. The next test starts without them: the
        // script ran in this test's transaction, and the rollback took them away.
        assertThat(repository.count()).isEqualTo(5);
        assertThat(repository.searchTitles("darkness"))
                .extracting(Book::getTitle)
                .containsExactly("The Left Hand of Darkness");
        assertThat(repository.findByAuthorsIdOrderByTitle(leGuinId()))
                .extracting(BookTitleOnly::getTitle)
                .containsExactly("The Dispossessed", "The Left Hand of Darkness");
    }

    private Long leGuinId() {
        return em.getEntityManager()
                .createQuery("select a.id from Author a where a.name = 'Ursula K. Le Guin'", Long.class)
                .getSingleResult();
    }

    @Test
    void theDatabaseRejectsADuplicateIsbn() {
        // The unique constraint from V1. With IDENTITY ids a plain save() already sends the INSERT
        // (Hibernate needs the generated id); saveAndFlush makes that explicit, and it would matter
        // with SEQUENCE ids, where the INSERT waits for the flush.
        assertThatThrownBy(() -> repository.saveAndFlush(new Book("9780441013593", "Dune (copy)", 1965, 1)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("books_isbn_key");
    }

    @Test
    void persistMakesTheBookManagedAndAssignsTheId() {
        Book book = new Book("9780141439518", "Pride and Prejudice", 1813, 4);
        assertThat(book.getId()).isNull(); // transient: Hibernate does not know it

        em.persist(book);

        // IDENTITY ids come from the INSERT, so Hibernate runs it straight away.
        assertThat(book.getId()).isNotNull();
        assertThat(em.getEntityManager().contains(book)).isTrue(); // managed
    }

    @Test
    void dirtyCheckingSavesChangesWithoutCallingSave() {
        // Saved in @BeforeEach, inside this test's transaction: still managed.
        dune.setTotalCopies(10); // no save() call
        em.flush(); // Hibernate compares it with its snapshot and sends an UPDATE
        em.clear(); // forget every loaded entity, so the next find reads the row again

        assertThat(repository.findById(dune.getId()))
                .get()
                .extracting(Book::getTotalCopies)
                .isEqualTo(10);
    }
}
