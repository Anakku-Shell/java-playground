package dev.playground.library.book;

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
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.common.PageResponse;
import dev.playground.library.loan.ActiveLoanCount;
import dev.playground.library.loan.LoanRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The book rules, with the repositories mocked: no Spring and no database, so it runs in
 * milliseconds. What the queries return against a real database is the job of
 * {@code BookRepositoryIT} and {@code BookQueriesIT}. Guide: §5.4, §5.5 Advanced JPA.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository books;

    @Mock
    private AuthorRepository authors;

    @Mock
    private LoanRepository loans;

    @InjectMocks
    private BookService service;

    private final Author herbert = withId(new Author("Frank Herbert", 1920), 1L);
    private final Author leGuin = withId(new Author("Ursula K. Le Guin", 1929), 2L);

    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    private static Book stored(Long id, String isbn, String title, int totalCopies, Author... writers) {
        Book book = withId(new Book(isbn, title, 1965, totalCopies), id);
        book.replaceAuthors(Set.of(writers));
        return book;
    }

    /** save() returns the entity with its generated id, like the real one. */
    private void saveAssignsId(Long id) {
        given(books.save(any(Book.class))).willAnswer(invocation -> withId(invocation.getArgument(0), id));
    }

    @Test
    void createLinksTheAuthorsAndStoresAnIsbn13() {
        given(authors.findAllById(Set.of(1L))).willReturn(List.of(herbert));
        saveAssignsId(10L);

        BookResponse created = service.create(new CreateBookRequest("0-441-01359-7", "Dune", 1965, 3, Set.of(1L)));

        // A new book has no loans: every copy is available.
        assertThat(created)
                .isEqualTo(new BookResponse(
                        10L, "9780441013593", "Dune", 1965, 3, 3, List.of(new AuthorSummary(1L, "Frank Herbert"))));
    }

    @Test
    void anUnknownAuthorIdIsNotFoundAndNothingIsSaved() {
        given(authors.findAllById(Set.of(1L, 9L))).willReturn(List.of(herbert));

        assertThatThrownBy(
                        () -> service.create(new CreateBookRequest("9780441013593", "Dune", 1965, 3, Set.of(1L, 9L))))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Author 9 not found");
        then(books).should(never()).save(any());
    }

    @Test
    void missingAuthorIdsMeanNoAuthors() {
        saveAssignsId(10L);

        BookResponse created = service.create(new CreateBookRequest("9780441013593", "Dune", 1965, 3, null));

        assertThat(created.authors()).isEmpty();
        then(authors).shouldHaveNoInteractions();
    }

    @Test
    void duplicateIsbnOnCreateIsAConflictAndNothingIsSaved() {
        // The ISBN-10 of a stored ISBN-13 is the same book: the check uses the canonical form.
        given(books.existsByIsbn("9780441013593")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CreateBookRequest("0441013597", "Dune again", 1965, 1, Set.of())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A book with ISBN 9780441013593 already exists");
        then(books).should(never()).save(any());
    }

    @Test
    void updateChangesTheManagedBookAndItsAuthors() {
        Book dune = stored(1L, "9780441013593", "Dune", 3, herbert);
        given(books.findById(1L)).willReturn(Optional.of(dune));
        given(authors.findAllById(Set.of(2L))).willReturn(List.of(leGuin));

        BookResponse updated = service.update(
                1L, new UpdateBookRequest("9780441013593", "Dune (40th anniversary)", 2005, 5, Set.of(2L)));

        assertThat(updated.title()).isEqualTo("Dune (40th anniversary)");
        assertThat(dune.getTotalCopies()).isEqualTo(5);
        assertThat(dune.getAuthors()).containsExactly(leGuin);
        // No save(): inside the service's transaction the entity is managed, and Hibernate writes
        // the changes on commit (dirty checking), the book_authors rows included.
        then(books).should(never()).save(any());
    }

    @Test
    void updateCannotTakeAnotherBooksIsbn() {
        Book emma = stored(2L, "9780141439587", "Emma", 2);
        given(books.findById(2L)).willReturn(Optional.of(emma));
        given(books.existsByIsbnAndIdNot("9780441013593", 2L)).willReturn(true);

        assertThatThrownBy(() -> service.update(2L, new UpdateBookRequest("9780441013593", "Emma", 1815, 2, Set.of())))
                .isInstanceOf(ConflictException.class);
        // Checked before the entity is touched (BookService.update explains why).
        assertThat(emma.getIsbn()).isEqualTo("9780141439587");
    }

    @Test
    void totalCopiesMayEqualTheCopiesOnLoan() {
        Book dune = stored(1L, "9780441013593", "Dune", 3);
        given(books.findById(1L)).willReturn(Optional.of(dune));
        given(loans.countByBookIdAndReturnedAtIsNull(1L)).willReturn(2L);
        BookResponse updated = service.update(1L, new UpdateBookRequest("9780441013593", "Dune", 1965, 2, Set.of()));
        assertThat(updated.availableCopies()).isZero();
    }

    @Test
    void totalCopiesCannotDropBelowTheCopiesOnLoan() {
        Book dune = stored(1L, "9780441013593", "Dune", 3);
        given(books.findById(1L)).willReturn(Optional.of(dune));
        given(loans.countByBookIdAndReturnedAtIsNull(1L)).willReturn(2L);

        assertThatThrownBy(() -> service.update(1L, new UpdateBookRequest("9780441013593", "Dune", 1965, 1, Set.of())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Book 1 has 2 copies on loan; totalCopies cannot be lower");
        assertThat(dune.getTotalCopies()).isEqualTo(3);
    }

    @Test
    void availableCopiesAreTotalMinusActiveLoans() {
        given(books.findWithAuthorsById(1L)).willReturn(Optional.of(stored(1L, "9780441013593", "Dune", 3, herbert)));
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
        Book dune = stored(1L, "9780441013593", "Dune", 3, herbert);
        Book messiah = stored(2L, "9780593098233", "Dune Messiah", 2, herbert);
        Page<Book> page = new PageImpl<>(List.of(dune, messiah), sent, 2);
        given(books.findAll(any(Specification.class), eq(sent))).willReturn(page);
        given(loans.countActiveByBookIds(List.of(1L, 2L))).willReturn(List.of(new ActiveLoanCount(1L, 1)));

        PageResponse<BookResponse> result = service.findAll("dune", null, requested);

        assertThat(result.content()).extracting(BookResponse::availableCopies).containsExactly(2, 2);
        assertThat(result)
                .extracting(
                        PageResponse::page, PageResponse::size, PageResponse::totalElements, PageResponse::totalPages)
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
    void missingIdIsNotFound() {
        given(books.findWithAuthorsById(42L)).willReturn(Optional.empty());
        given(books.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book 42 not found");
        assertThatThrownBy(() -> service.update(42L, new UpdateBookRequest("9780441013593", "x", null, 1, Set.of())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(NotFoundException.class);
        then(books).should(never()).deleteById(any());
    }

    @Test
    void deleteRemovesABookWithoutLoans() {
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
}
