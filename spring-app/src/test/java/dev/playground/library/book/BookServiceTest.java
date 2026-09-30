package dev.playground.library.book;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The book rules, with the repository mocked: no Spring and no database, so it runs in
 * milliseconds. What the queries return against a real database is {@code BookRepositoryIT}'s job.
 * Guide: §5.4 Persistence with JPA.
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

    @Mock
    private BookRepository repository;

    @InjectMocks
    private BookService service;

    private static Book stored(Long id, String isbn, String title) {
        Book book = new Book(isbn, title, 1965, 3);
        ReflectionTestUtils.setField(book, "id", id);
        return book;
    }

    /** save() returns the entity with its generated id, like the real one. */
    private void saveAssignsId(Long id) {
        given(repository.save(any(Book.class))).willAnswer(invocation -> {
            Book book = invocation.getArgument(0);
            ReflectionTestUtils.setField(book, "id", id);
            return book;
        });
    }

    @Test
    void createSavesTheBookWithAnIsbn13() {
        saveAssignsId(1L);

        BookResponse created = service.create(new CreateBookRequest("0-441-01359-7", "Dune", 1965, 3, Set.of()));

        // Loans and authors arrive in §5.5: every copy is available and the authors list is empty.
        assertThat(created).isEqualTo(new BookResponse(1L, "9780441013593", "Dune", 1965, 3, 3, List.of()));
    }

    @Test
    void duplicateIsbnOnCreateIsAConflictAndNothingIsSaved() {
        // The ISBN-10 of a stored ISBN-13 is the same book: the check uses the canonical form.
        given(repository.existsByIsbn("9780441013593")).willReturn(true);

        assertThatThrownBy(() -> service.create(new CreateBookRequest("0441013597", "Dune again", 1965, 1, Set.of())))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A book with ISBN 9780441013593 already exists");
        then(repository).should(never()).save(any());
    }

    @Test
    void updateChangesTheManagedBook() {
        Book dune = stored(1L, "9780441013593", "Dune", 3);
        given(repository.findById(1L)).willReturn(Optional.of(dune));

        BookResponse updated = service.update(
                1L, new UpdateBookRequest("9780441013593", "Dune (40th anniversary)", 2005, 5, Set.of()));

        assertThat(updated.title()).isEqualTo("Dune (40th anniversary)");
        assertThat(dune.getTotalCopies()).isEqualTo(5);
        // No save(): inside the service's transaction the entity is managed, and Hibernate writes
        // the changes on commit (dirty checking). BookRepositoryIT shows it on a real database.
        then(repository).should(never()).save(any());
    }

    @Test
    void updateCannotTakeAnotherBooksIsbn() {
        Book emma = stored(2L, "9780141439587", "Emma", 2);
        given(repository.findById(2L)).willReturn(Optional.of(emma));
        given(repository.existsByIsbnAndIdNot("9780441013593", 2L)).willReturn(true);

        assertThatThrownBy(() -> service.update(2L, new UpdateBookRequest("9780441013593", "Emma", 1815, 2, Set.of())))
                .isInstanceOf(ConflictException.class);
        // Checked before the entity is touched (BookService.update explains why).
        assertThat(emma.getIsbn()).isEqualTo("9780141439587");
    }

    @Test
    void findAllWithoutTitleListsEverythingById() {
        given(repository.findAll(Sort.by("id"))).willReturn(List.of(stored(1L, "9780441013593", "Dune", 3)));

        assertThat(service.findAll(null)).extracting(BookResponse::title).containsExactly("Dune");
        assertThat(service.findAll(" ")).extracting(BookResponse::title).containsExactly("Dune");
    }

    @Test
    void findAllWithTitleFilters() {
        given(repository.findByTitleContainingIgnoreCase("dune", Sort.by("id")))
                .willReturn(List.of(stored(1L, "9780441013593", "Dune", 3)));

        assertThat(service.findAll("dune")).extracting(BookResponse::title).containsExactly("Dune");
    }

    @Test
    void missingIdIsNotFound() {
        given(repository.findById(42L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(42L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Book 42 not found");
        assertThatThrownBy(() -> service.update(42L, new UpdateBookRequest("9780441013593", "x", null, 1, Set.of())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(42L)).isInstanceOf(NotFoundException.class);
        then(repository).should(never()).deleteById(any());
    }

    @Test
    void deleteRemovesAnExistingBook() {
        given(repository.existsById(1L)).willReturn(true);

        service.delete(1L);

        then(repository).should().deleteById(1L);
    }

    private static Book stored(Long id, String isbn, String title, int totalCopies) {
        Book book = stored(id, isbn, title);
        book.setTotalCopies(totalCopies);
        return book;
    }
}
