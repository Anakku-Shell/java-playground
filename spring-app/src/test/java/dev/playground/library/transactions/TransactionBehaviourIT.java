package dev.playground.library.transactions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.TestTables;
import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * How {@code @Transactional} behaves, on the real transaction manager and PostgreSQL. Each test
 * writes author rows and then looks at which ones were committed.
 *
 * <p>The outer transaction is programmatic: {@link TransactionTemplate} runs a lambda in a
 * transaction, commits if it returns, and rolls back if it throws (or calls
 * {@code setRollbackOnly()}). The inner ones are {@code @Transactional} methods of {@link Writer},
 * a separate bean, so each call goes through Spring's proxy. Guide: §5.6 Transactions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Import({TestcontainersConfiguration.class, TransactionBehaviourIT.Writer.class, TransactionBehaviourIT.SelfCaller.class
})
class TransactionBehaviourIT {

    /** Writes an author row with plain JDBC, inside whatever transaction the proxy gives it. */
    static class Writer {

        private final JdbcTemplate jdbc;
        private final AuthorRepository authors;

        Writer(JdbcTemplate jdbc, AuthorRepository authors) {
            this.jdbc = jdbc;
            this.authors = authors;
        }

        /** REQUIRED, the default: joins the caller's transaction, or starts one if there is none. */
        @Transactional
        public void joining(String name) {
            insert(name);
        }

        /** REQUIRES_NEW: suspends the caller's transaction and commits its own when it returns. */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void inItsOwnTransaction(String name) {
            insert(name);
        }

        @Transactional
        public void failing(String name) {
            insert(name);
            throw new IllegalStateException("inner failure");
        }

        /** Rollback rules: by default only unchecked exceptions (and errors) roll back. */
        @Transactional
        public void failingWithACheckedException(String name) throws IOException {
            insert(name);
            throw new IOException("checked failure");
        }

        @Transactional(rollbackFor = Exception.class)
        public void failingWithACheckedExceptionAndRollbackFor(String name) throws IOException {
            insert(name);
            throw new IOException("checked failure");
        }

        /** NESTED: a savepoint in the caller's transaction, if the transaction manager supports one. */
        @Transactional(propagation = Propagation.NESTED)
        public void nested(String name) {
            insert(name);
        }

        @Transactional(readOnly = true)
        public void insertInReadOnly(String name) {
            insert(name);
        }

        /** Changes a managed entity and returns: in a read-write transaction, the commit writes it. */
        @Transactional(readOnly = true)
        public void renameInReadOnly(Long id, String name) {
            authors.findById(id).orElseThrow().setName(name);
        }

        private void insert(String name) {
            jdbc.update("insert into authors (name) values (?)", name);
        }
    }

    /** Calls one of its own {@code @Transactional} methods: the self-invocation pitfall. */
    static class SelfCaller {

        @Transactional
        public String outerCallingInner() {
            return inner(); // this.inner(): a plain Java call on the real object, not on the proxy
        }

        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public String inner() {
            // Spring names a declarative transaction after the method that started it.
            return TransactionSynchronizationManager.getCurrentTransactionName();
        }
    }

    @Autowired
    private Writer writer;

    @Autowired
    private SelfCaller selfCaller;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        TestTables.truncateAll(jdbc);
    }

    private List<String> committed() {
        return jdbc.queryForList("select name from authors order by id", String.class);
    }

    @Test
    void requiredJoinsTheCallersTransaction() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    writer.joining("Joined");
                    throw new IllegalStateException("outer failure");
                }))
                .hasMessage("outer failure");

        // One transaction: the outer rollback took the inner write with it.
        assertThat(committed()).isEmpty();
    }

    @Test
    void requiresNewCommitsOnItsOwn() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    writer.inItsOwnTransaction("Own");
                    writer.joining("Joined");
                    throw new IllegalStateException("outer failure");
                }))
                .hasMessage("outer failure");

        // "Own" committed when its method returned, before the outer failure.
        assertThat(committed()).containsExactly("Own");
    }

    @Test
    void catchingAnInnerFailureDoesNotSaveTheCallersTransaction() {
        // The inner method joined the outer transaction. Its exception crossed the proxy, which
        // marked the whole (shared) transaction rollback-only. Catching the exception does not undo
        // that mark: the outer commit finds it, rolls back, and says so.
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                    writer.joining("Before");
                    try {
                        writer.failing("Failed");
                    } catch (IllegalStateException handled) {
                        // "handled", and the outer method goes on to commit
                    }
                }))
                .isInstanceOf(UnexpectedRollbackException.class)
                .hasMessageContaining("rollback-only");

        assertThat(committed()).isEmpty();
    }

    @Test
    void nestedIsNotSupportedByTheJpaTransactionManager() {
        // A savepoint would roll back the database but not the persistence context, whose entities
        // would keep changes the database dropped. JpaTransactionManager refuses instead of guessing.
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> writer.nested("Nested")))
                .isInstanceOf(NestedTransactionNotSupportedException.class);

        assertThat(committed()).isEmpty();
    }

    @Test
    void aCheckedExceptionCommitsByDefault() {
        assertThatThrownBy(() -> writer.failingWithACheckedException("Checked")).isInstanceOf(IOException.class);

        // The method failed, and its write was committed anyway.
        assertThat(committed()).containsExactly("Checked");
    }

    @Test
    void rollbackForMakesACheckedExceptionRollBack() {
        assertThatThrownBy(() -> writer.failingWithACheckedExceptionAndRollbackFor("Checked"))
                .isInstanceOf(IOException.class);

        assertThat(committed()).isEmpty();
    }

    @Test
    void selfInvocationSkipsTheProxy() {
        // Through the proxy, inner() gets its own transaction...
        assertThat(selfCaller.inner()).endsWith("SelfCaller.inner");
        // ...but called from outerCallingInner() it runs in the outer one: REQUIRES_NEW was ignored,
        // because the proxy never saw the call.
        assertThat(selfCaller.outerCallingInner()).endsWith("SelfCaller.outerCallingInner");
    }

    @Test
    void aReadOnlyTransactionDoesNotWrite() {
        Long id = authors.save(new Author("Original", 1900)).getId();

        // Hibernate: a read-only transaction does not flush, so dirty checking never sends the UPDATE.
        writer.renameInReadOnly(id, "Changed");
        assertThat(committed()).containsExactly("Original");

        // PostgreSQL: the connection is read-only too, and the database refuses a write outright.
        assertThatThrownBy(() -> writer.insertInReadOnly("Sneaky"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("read-only transaction");
    }
}
