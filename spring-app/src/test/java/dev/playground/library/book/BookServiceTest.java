package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Book use cases on the real in-memory repository, without Spring. Guide: §5.2 REST API. */
class BookServiceTest {

    private final BookService service = new BookService(new BookRepository());

    private static CreateBookRequest dune() {
        return new CreateBookRequest("9780441013593", "Dune", 1965, 3, Set.of());
    }

    @Test
    void createFindUpdateDeleteRoundTrip() {
        BookResponse created = service.create(dune());
        assertThat(service.findById(created.id())).isEqualTo(created);

        BookResponse updated = service.update(
                created.id(), new UpdateBookRequest("9780441013593", "Dune (40th anniversary)", 2005, 5, Set.of()));
        assertThat(updated.title()).isEqualTo("Dune (40th anniversary)");
        assertThat(updated.totalCopies()).isEqualTo(5);
        assertThat(service.findAll()).containsExactly(updated);

        service.delete(created.id());
        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void responseShowsAllCopiesAvailableUntilLoansExist() {
        // Loans and authors arrive in §5.5. Until then every copy is available and the author
        // list is empty (the request's authorIds are ignored).
        BookResponse created = service.create(new CreateBookRequest("9780141439518", "Emma", 1815, 2, Set.of(1L)));

        assertThat(created).isEqualTo(new BookResponse(created.id(), "9780141439518", "Emma", 1815, 2, 2, List.of()));
    }

    @Test
    void missingIdIsNotFound() {
        assertThatThrownBy(() -> service.findById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book 42 not found");
        assertThatThrownBy(() -> service.update(42L, new UpdateBookRequest("9780441013593", "x", null, 1, Set.of())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void isbn10IsStoredAsIsbn13() {
        // The same book can be written as ISBN-10 or ISBN-13, with or without hyphens. Storing one
        // canonical form makes the duplicate check (and a later database unique constraint) work.
        BookResponse created = service.create(new CreateBookRequest("0-441-01359-7", "Dune", 1965, 3, Set.of()));

        assertThat(created.isbn()).isEqualTo("9780441013593");
    }

    @Test
    void duplicateIsbnIsAConflict() {
        service.create(dune());

        // The ISBN-10 of an existing ISBN-13 is the same book.
        assertThatThrownBy(() -> service.create(new CreateBookRequest("0441013597", "Dune again", 1965, 1, Set.of())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A book with ISBN 9780441013593 already exists");
    }

    @Test
    void updateCannotTakeAnotherBooksIsbnButMayKeepItsOwn() {
        BookResponse dune = service.create(dune());
        BookResponse emma = service.create(new CreateBookRequest("9780141439518", "Emma", 1815, 2, Set.of()));

        assertThatThrownBy(
                        () -> service.update(emma.id(), new UpdateBookRequest(dune.isbn(), "Emma", 1815, 2, Set.of())))
                .isInstanceOf(ConflictException.class);
        // The rejected update left Emma untouched: the check runs before apply() changes the stored
        // object in place.
        assertThat(service.findById(emma.id()).isbn()).isEqualTo("9780141439518");
        assertThat(service.update(dune.id(), new UpdateBookRequest(dune.isbn(), "Dune", 1965, 5, Set.of()))
                        .totalCopies())
                .isEqualTo(5);
    }

    @Test
    void idsAreNotReused() {
        BookResponse first = service.create(dune());
        service.delete(first.id());

        BookResponse second = service.create(dune());

        // Like a database sequence: a deleted id is never handed out again, so an old link
        // cannot suddenly point at a different book.
        assertThat(second.id()).isGreaterThan(first.id());
    }
}
