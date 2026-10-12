package dev.playground.library.book;

import static dev.playground.library.testing.TestDataFactory.ada;
import static dev.playground.library.testing.TestDataFactory.herbert;
import static dev.playground.library.testing.TestDataFactory.leGuin;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import dev.playground.library.TestcontainersConfiguration;
import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.common.PageResponse;
import dev.playground.library.config.JpaAuditingConfig;
import dev.playground.library.loan.Loan;
import dev.playground.library.loan.LoanRepository;
import dev.playground.library.member.Member;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.hibernate.LazyInitializationException;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * The book–author relation against PostgreSQL: which side writes the join table, lazy loading, and
 * how many SQL statements each way of loading costs. Hibernate's {@code Statistics} (switched on
 * for this test only) counts the statements sent. Every test starts from an empty persistence
 * context ({@code clear()}), as a new request would. Guide: §5.5 Advanced JPA, §5.8 Testing (the spy).
 */
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
// BookService is not part of the JPA slice; importing it adds just that bean.
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class, BookService.class})
class BookQueriesIT {

    @Autowired
    private BookService service;

    @Autowired
    private BookRepository books;

    @Autowired
    private AuthorRepository authors;

    @Autowired
    private TestEntityManager em;

    // A spy wraps the real repository bean: every call goes through to PostgreSQL as usual, and
    // Mockito records it, so the test can verify the calls or stub just one method. (A @MockitoBean
    // would replace the bean with an empty mock.) It changes the context's cache key like any
    // bean override; this class already has a context of its own, so it costs no extra one.
    @MockitoSpyBean
    private LoanRepository loans;

    private Statistics statistics;

    private Author herbert;
    private Author leGuin;
    private Book dune;

    @BeforeEach
    void saveBooksWithAuthors() {
        herbert = authors.save(herbert());
        leGuin = authors.save(leGuin());
        dune = saveBook("9780441013593", "Dune", herbert);
        saveBook("9780593098233", "Dune Messiah", herbert);
        saveBook("9780061054884", "The Dispossessed", leGuin);
        saveBook("9780441478125", "The Left Hand of Darkness", leGuin);
        em.flush();
        em.clear();
        statistics = em.getEntityManager()
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        statistics.clear();
    }

    private Book saveBook(String isbn, String title, Author author) {
        Book book = new Book(isbn, title, 1970, 1);
        book.replaceAuthors(Set.of(author));
        return books.save(book);
    }

    private long statements() {
        return statistics.getPrepareStatementCount();
    }

    @Test
    void linksAreWrittenThroughTheOwningSide() {
        Book book = books.findById(dune.getId()).orElseThrow();

        book.replaceAuthors(Set.of(em.find(Author.class, herbert.getId()), em.find(Author.class, leGuin.getId())));
        em.flush(); // dirty checking covers collections too: one INSERT into book_authors
        em.clear();

        assertThat(books.findWithAuthorsById(dune.getId()).orElseThrow().getAuthors())
                .extracting(Author::getName)
                .containsExactlyInAnyOrder("Frank Herbert", "Ursula K. Le Guin");
    }

    @Test
    void theInverseSideReadsTheSameJoinTable() {
        // Author.books is mappedBy = "authors": no mapping of its own, it reads book_authors.
        assertThat(em.find(Author.class, herbert.getId()).getBooks())
                .extracting(Book::getTitle)
                .containsExactlyInAnyOrder("Dune", "Dune Messiah");
    }

    @Test
    void aLazyCollectionCannotLoadOnceTheBookIsDetached() {
        Book book = books.findById(dune.getId()).orElseThrow();
        em.clear(); // the persistence context is gone, as after the service's transaction ends

        // The set is an uninitialised placeholder; loading it needs an open session.
        assertThatThrownBy(() -> book.getAuthors().size())
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("Cannot lazily initialize collection of role");
    }

    @Test
    void anEntityGraphFetchesTheAuthorsInTheSameStatement() {
        // Lazy: one SELECT for the book, a second one when the authors are touched.
        books.findById(dune.getId()).orElseThrow().getAuthors().size();
        assertThat(statements()).isEqualTo(2);

        em.clear();
        statistics.clear();

        // @EntityGraph(attributePaths = "authors"): one SELECT with a left join.
        books.findWithAuthorsById(dune.getId()).orElseThrow().getAuthors().size();
        assertThat(statements()).isEqualTo(1);
    }

    @Test
    void touchingEveryBooksAuthorsIsOneBatchNotOneQueryPerBook() {
        List<Book> all = books.findAll(Sort.by("id"));

        all.forEach(book -> book.getAuthors().size());

        // Without @BatchSize: 1 + 4 (one SELECT per book: the N+1 problem). With it, the first
        // touch loads the authors of every book in the persistence context in one statement.
        assertThat(statements()).isEqualTo(2);
    }

    @Test
    void listingAPageOfBooksTakesThreeStatements() {
        PageResponse<BookResponse> page = service.findAll(null, null, PageRequest.of(0, 20, Sort.by("title")));

        assertThat(page.content())
                .hasSize(4)
                .allSatisfy(book -> assertThat(book.authors()).hasSize(1));
        // 1. the page of books, 2. the active loans of all of them (one grouped count), 3. their
        // authors, in one batch, when the mapping touches the first book's authors. No count(*): a
        // first page shorter than the page size is the whole result, so Spring Data skips it. The
        // same 3 for a page of 100.
        assertThat(statements()).isEqualTo(3);
    }

    @Test
    void aPageAsksForTheLoansOfAllItsBooksAtOnce() {
        service.findAll(null, null, PageRequest.of(0, 20, Sort.by("title")));

        // The same N+1 guard as the statement count above, said in terms of the code: one grouped
        // count for the page, never the one-book count findById uses.
        then(loans).should(times(1)).countActiveByBookIds(anyCollection());
        then(loans).should(never()).countByBookIdAndReturnedAtIsNull(any());
    }

    @Test
    void aSpyCanStubOneMethodAndKeepTheRest() {
        // A real loan of Dune's only copy.
        Member ada = em.persist(ada());
        em.persist(new Loan(
                em.find(Book.class, dune.getId()),
                ada,
                Instant.parse("2026-09-30T10:00:00Z"),
                LocalDate.of(2026, 10, 14)));
        em.flush();
        // Stub one method of the spy: the grouped count now says nothing is out. Stub a spy with
        // willReturn(..).given(spy): given(spy.method(..)) would first call the real method.
        willReturn(List.of()).given(loans).countActiveByBookIds(anyCollection());

        PageResponse<BookResponse> page = service.findAll("dune", null, PageRequest.of(0, 20, Sort.by("title")));

        assertThat(page.content())
                .extracting(BookResponse::title, BookResponse::availableCopies)
                .containsExactly(tuple("Dune", 1), tuple("Dune Messiah", 1));
        // Every other method is still the real one, and sees the loan. (A mock would answer 0.)
        assertThat(loans.countByBookIdAndReturnedAtIsNull(dune.getId())).isEqualTo(1);
    }

    @Test
    void derivedQueriesCanFollowTheRelation() {
        // existsByAuthorsId: "books whose authors include this id" (a join on book_authors).
        assertThat(books.existsByAuthorsId(herbert.getId())).isTrue();
        assertThat(books.existsByAuthorsId(
                        authors.save(new Author("Nobody", null)).getId()))
                .isFalse();
    }

    @Test
    void anInterfaceProjectionSelectsOnlyItsColumns() {
        // Spring Data implements BookTitleOnly and selects just id and title: no entity, nothing
        // to track. The SQL log shows the narrower SELECT.
        assertThat(books.findByAuthorsIdOrderByTitle(leGuin.getId()))
                .extracting(BookTitleOnly::getTitle)
                .containsExactly("The Dispossessed", "The Left Hand of Darkness");
    }

    @Test
    void specificationsComposeOptionalFilters() {
        Specification<Book> duneByHerbert = Specification.allOf(
                BookSpecifications.titleContains("dUNE"), BookSpecifications.hasAuthor(herbert.getId()));
        assertThat(books.findAll(duneByHerbert, Sort.by("title")))
                .extracting(Book::getTitle)
                .containsExactly("Dune", "Dune Messiah");

        assertThat(books.findAll(BookSpecifications.hasAuthor(leGuin.getId()), Sort.by("title")))
                .extracting(Book::getTitle)
                .containsExactly("The Dispossessed", "The Left Hand of Darkness");

        // A missing filter adds no condition.
        assertThat(books.findAll(Specification.allOf(
                        BookSpecifications.titleContains(null), BookSpecifications.hasAuthor(null))))
                .hasSize(4);
    }

    @Test
    void theTitleFilterFoldsCaseLikeTheDatabase() {
        // Java upper-cases "ß" to "SS", PostgreSQL keeps "ß": both sides must be folded by the same one.
        saveBook("9783462050363", "Die Straße", leGuin);

        assertThat(books.findAll(BookSpecifications.titleContains("straße")))
                .extracting(Book::getTitle)
                .containsExactly("Die Straße");
    }

    @Test
    void likeWildcardsInTheTitleFilterAreLiteral() {
        // Unescaped, "%" would match every title.
        assertThat(books.findAll(BookSpecifications.titleContains("%"))).isEmpty();
        assertThat(books.findAll(BookSpecifications.titleContains("_une"))).isEmpty();
    }

    @Test
    void aPageHoldsOneSliceAndKnowsTheTotal() {
        Page<Book> first =
                books.findAll(BookSpecifications.titleContains("the"), PageRequest.of(0, 1, Sort.by("title")));

        assertThat(first.getContent()).extracting(Book::getTitle).containsExactly("The Dispossessed");
        assertThat(first.getTotalElements()).isEqualTo(2); // a second, count(*) query
        assertThat(first.getTotalPages()).isEqualTo(2);
    }
}
