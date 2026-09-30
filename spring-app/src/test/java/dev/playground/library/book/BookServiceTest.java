package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    void missingIdIs404() {
        assertThatThrownBy(() -> service.findById(42L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Book 42 not found");
        assertThatThrownBy(() -> service.update(42L, new UpdateBookRequest("1", "x", null, 1, Set.of())))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(ResponseStatusException.class);
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
