package dev.playground.library.book;

import static dev.playground.library.testing.TestDataFactory.aBookRequest;
import static dev.playground.library.testing.TestDataFactory.dune;
import static dev.playground.library.testing.TestDataFactory.duneMessiah;
import static dev.playground.library.testing.TestDataFactory.emma;
import static dev.playground.library.testing.TestDataFactory.herbert;
import static dev.playground.library.testing.TestDataFactory.leGuin;
import static dev.playground.library.testing.TestDataFactory.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import dev.playground.library.book.dto.AuthorSummary;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.common.PageResponse;
import dev.playground.library.loan.ActiveLoanCount;
import dev.playground.library.loan.LoanRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * The book rules, with the repositories mocked: no Spring and no database, so it runs in
 * milliseconds. What the queries return against a real database is the job of
 * {@code BookRepositoryIT} and {@code BookQueriesIT}. The tests are grouped per use case in
 * {@code @Nested} classes: each group reads as a small spec, and the report shows it as a tree.
 * Guide: §5.4, §5.5 Advanced JPA, §5.8 Testing.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    // The fields of the outer class are shared by the nested classes: one set of mocks, rebuilt
    // for every test (JUnit creates a new instance of both classes per test).
    @Mock
    private BookRepository books;

    @Mock
    private AuthorRepository authors;

    @Mock
    private LoanRepository loans;

    @InjectMocks
    private BookService service;

    // Records the arguments of a call, so the test can look at them afterwards.
    @Captor
    private ArgumentCaptor<Book> savedBook;

    private final Author herbert = withId(herbert(), 1L);
    private final Author leGuin = withId(leGuin(), 2L);

    private static Book stored(Book book, long id, Author... writers) {
        book.replaceAuthors(Set.of(writers));
        return withId(book, id);
    }

    @Nested
    class Create {

        /** save() returns the entity with its generated id, like the real one. */
        private void saveAssignsId(long id) {
            given(books.save(any(Book.class))).willAnswer(invocation -> withId(invocation.getArgument(0), id));
        }

        @Test
        void linksTheAuthorsAndStoresAnIsbn13() {
            given(authors.findAllById(Set.of(1L))).willReturn(List.of(herbert));
            saveAssignsId(10L);

            BookResponse created = service.create(
                    aBookRequest().isbn("0-441-01359-7").authorIds(1L).create());

            // A new book has no loans: every copy is available.
            assertThat(created)
                    .isEqualTo(new BookResponse(
                            10L, "9780441013593", "Dune", 1965, 3, 3, List.of(new AuthorSummary(1L, "Frank Herbert"))));
        }

        @Test
        void savesTheEntityItBuiltFromTheRequest() {
            // The response above could be built without saving the right thing. The captor shows
            // what reached the repository: the canonical ISBN and the author links.
            given(authors.findAllById(Set.of(1L, 2L))).willReturn(List.of(herbert, leGuin));
            saveAssignsId(10L);

            service.create(aBookRequest().isbn("0441013597").authorIds(1L, 2L).create());

            then(books).should().save(savedBook.capture());
            assertThat(savedBook.getValue().getIsbn()).isEqualTo("9780441013593");
            assertThat(savedBook.getValue().getAuthors()).containsExactlyInAnyOrder(herbert, leGuin);
        }

        @Test
        void anUnknownAuthorIdIsNotFoundAndNothingIsSaved() {
            given(authors.findAllById(Set.of(1L, 9L))).willReturn(List.of(herbert));

            assertThatThrownBy(() ->
                            service.create(aBookRequest().authorIds(1L, 9L).create()))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Author 9 not found");
            then(books).should(never()).save(any());
        }

        @Test
        void missingAuthorIdsMeanNoAuthors() {
            saveAssignsId(10L);

            BookResponse created =
                    service.create(aBookRequest().withoutAuthorIds().create());

            assertThat(created.authors()).isEmpty();
            then(authors).shouldHaveNoInteractions();
        }

        @Test
        void aDuplicateIsbnIsAConflictAndNothingIsSaved() {
            // The ISBN-10 of a stored ISBN-13 is the same book: the check uses the canonical form.
            given(books.existsByIsbn("9780441013593")).willReturn(true);

            assertThatThrownBy(() -> service.create(aBookRequest()
                            .isbn("0441013597")
                            .title("Dune again")
                            .create()))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("A book with ISBN 9780441013593 already exists");
            then(books).should(never()).save(any());
        }
    }

    @Nested
    class Update {

        @Test
        void changesTheManagedBookAndItsAuthors() {
            Book dune = stored(dune(), 1L, herbert);
            given(books.findById(1L)).willReturn(Optional.of(dune));
            given(authors.findAllById(Set.of(2L))).willReturn(List.of(leGuin));

            BookResponse updated = service.update(
                    1L,
                    aBookRequest()
                            .title("Dune (40th anniversary)")
                            .publishedYear(2005)
                            .totalCopies(5)
                            .authorIds(2L)
                            .update());

            assertThat(updated.title()).isEqualTo("Dune (40th anniversary)");
            assertThat(dune.getTotalCopies()).isEqualTo(5);
            assertThat(dune.getAuthors()).containsExactly(leGuin);
            // No save(): inside the service's transaction the entity is managed, and Hibernate
            // writes the changes on commit (dirty checking), the book_authors rows included.
            then(books).should(never()).save(any());
        }

        @Test
        void cannotTakeAnotherBooksIsbn() {
            Book emma = stored(emma(), 2L);
            given(books.findById(2L)).willReturn(Optional.of(emma));
            given(books.existsByIsbnAndIdNot("9780441013593", 2L)).willReturn(true);

            assertThatThrownBy(() ->
                            service.update(2L, aBookRequest().title("Emma").update()))
                    .isInstanceOf(ConflictException.class);
            // Checked before the entity is touched (BookService.update explains why).
            assertThat(emma.getIsbn()).isEqualTo("9780141439587");
        }

        // One row per case: the name template puts the values in the report, so a failing row
        // says which one. The boundary (2 copies, 2 on loan) is the row most likely to break.
        @ParameterizedTest(name = "{0} copies with {1} on loan: allowed = {2}")
        @CsvSource({"3, 2, true", "2, 2, true", "1, 2, false", "0, 0, true"})
        void totalCopiesCannotDropBelowTheCopiesOnLoan(int totalCopies, long onLoan, boolean allowed) {
            Book dune = stored(dune(), 1L);
            given(books.findById(1L)).willReturn(Optional.of(dune));
            given(loans.countByBookIdAndReturnedAtIsNull(1L)).willReturn(onLoan);

            if (allowed) {
                BookResponse updated = service.update(
                        1L, aBookRequest().totalCopies(totalCopies).update());
                assertThat(updated.availableCopies()).isEqualTo(totalCopies - onLoan);
            } else {
                assertThatThrownBy(() -> service.update(
                                1L, aBookRequest().totalCopies(totalCopies).update()))
                        .isInstanceOf(ConflictException.class)
                        .hasMessage("Book 1 has " + onLoan + " copies on loan; totalCopies cannot be lower");
                assertThat(dune.getTotalCopies()).isEqualTo(3);
            }
        }
    }

    @Nested
    class Read {

        @Test
        void availableCopiesAreTotalMinusActiveLoans() {
            given(books.findWithAuthorsById(1L)).willReturn(Optional.of(stored(dune(), 1L, herbert)));
            given(loans.countByBookIdAndReturnedAtIsNull(1L)).willReturn(1L);

            BookResponse dune = service.findById(1L);

            assertThat(dune.totalCopies()).isEqualTo(3);
            assertThat(dune.availableCopies()).isEqualTo(2);
        }

        @Test
        void aPageIsMappedWithOneLoanCountForAllItsBooks() {
            Pageable requested = PageRequest.of(0, 20, Sort.by("title"));
            // The service adds id as a tie-breaker, so equal titles keep one order across pages.
            Pageable sent = PageRequest.of(0, 20, Sort.by("title", "id"));
            Book dune = stored(dune(), 1L, herbert);
            Book messiah = stored(duneMessiah(), 2L, herbert);
            Page<Book> page = new PageImpl<>(List.of(dune, messiah), sent, 2);
            given(books.findAll(any(Specification.class), eq(sent))).willReturn(page);
            given(loans.countActiveByBookIds(List.of(1L, 2L))).willReturn(List.of(new ActiveLoanCount(1L, 1)));

            PageResponse<BookResponse> result = service.findAll("dune", null, requested);

            // Dune: 3 copies, 1 on loan; Dune Messiah: 2 copies, none on loan.
            assertThat(result.content())
                    .extracting(BookResponse::availableCopies)
                    .containsExactly(2, 2);
            assertThat(result)
                    .extracting(
                            PageResponse::page,
                            PageResponse::size,
                            PageResponse::totalElements,
                            PageResponse::totalPages)
                    .containsExactly(0, 20, 2L, 1);
        }

        @Test
        void anEmptyPageNeedsNoLoanCount() {
            given(books.findAll(any(Specification.class), any(Pageable.class))).willReturn(Page.empty());

            assertThat(service.findAll("nothing", null, PageRequest.of(0, 20)).content())
                    .isEmpty();
            then(loans).should(never()).countActiveByBookIds(anyCollection());
        }

        @Test
        void aMissingIdIsNotFound() {
            given(books.findWithAuthorsById(42L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(42L))
                    .isInstanceOf(NotFoundException.class)
                    .hasMessage("Book 42 not found");
        }
    }

    @Nested
    class Delete {

        @Test
        void removesABookWithoutLoans() {
            given(books.existsById(1L)).willReturn(true);

            service.delete(1L);

            then(books).should().deleteById(1L);
        }

        @Test
        void aBookWithLoansCannotBeDeleted() {
            // The loans keep their history, and their foreign key would refuse the delete anyway.
            given(books.existsById(1L)).willReturn(true);
            given(loans.existsByBookId(1L)).willReturn(true);

            assertThatThrownBy(() -> service.delete(1L))
                    .isInstanceOf(ConflictException.class)
                    .hasMessage("Book 1 has loans and cannot be deleted");
            then(books).should(never()).deleteById(any());
        }

        @Test
        void aMissingIdIsNotFoundForUpdateAndDeleteToo() {
            given(books.findById(42L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(42L, aBookRequest().update()))
                    .isInstanceOf(NotFoundException.class);
            assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(NotFoundException.class);
            then(books).should(never()).deleteById(any());
        }
    }
}
