package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.playground.library.author.dto.AuthorBookResponse;
import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import dev.playground.library.book.BookRepository;
import dev.playground.library.book.BookTitleOnly;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.common.PageResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A plain unit test: no Spring context at all. Constructor injection is what makes this possible:
 * {@code @InjectMocks} builds the service with {@code new}, passing Mockito mocks of the repository
 * interfaces. Guide: §5.2 REST API, §5.4 Persistence with JPA, §5.5 Advanced JPA.
 */
@ExtendWith(MockitoExtension.class)
class AuthorServiceTest {

    @Mock
    private AuthorRepository repository;

    @Mock
    private BookRepository books;

    @InjectMocks
    private AuthorService service;

    private static Author stored(Long id, String name, Integer birthYear) {
        Author author = new Author(name, birthYear);
        ReflectionTestUtils.setField(author, "id", id);
        return author;
    }

    @Test
    void createReturnsTheSavedAuthor() {
        given(repository.save(any(Author.class))).willAnswer(invocation -> {
            Author author = invocation.getArgument(0);
            ReflectionTestUtils.setField(author, "id", 1L); // the database assigns it
            return author;
        });

        AuthorResponse created = service.create(new CreateAuthorRequest("Ursula K. Le Guin", 1929));

        assertThat(created).isEqualTo(new AuthorResponse(1L, "Ursula K. Le Guin", 1929));
    }

    @Test
    void findAllReturnsAPageWithIdAsTheLastSortKey() {
        var sent = PageRequest.of(0, 20, Sort.by("name", "id"));
        given(repository.findAll(sent))
                .willReturn(new PageImpl<>(
                        List.of(stored(2L, "Frank Herbert", 1920), stored(1L, "Jane Austen", 1775)), sent, 2));

        PageResponse<AuthorResponse> page = service.findAll(PageRequest.of(0, 20, Sort.by("name")));

        assertThat(page.content())
                .containsExactly(
                        new AuthorResponse(2L, "Frank Herbert", 1920), new AuthorResponse(1L, "Jane Austen", 1775));
        assertThat(page.totalElements()).isEqualTo(2);
    }

    @Test
    void updateChangesTheManagedAuthor() {
        Author author = stored(1L, "Carl Sagn", null);
        given(repository.findById(1L)).willReturn(Optional.of(author));

        AuthorResponse updated = service.update(1L, new UpdateAuthorRequest("Carl Sagan", 1934));

        assertThat(updated).isEqualTo(new AuthorResponse(1L, "Carl Sagan", 1934));
        assertThat(author.getName()).isEqualTo("Carl Sagan");
        then(repository).should(never()).save(any()); // dirty checking writes it (§5.4)
    }

    @Test
    void deleteRemovesAnAuthorWithoutBooks() {
        given(repository.existsById(1L)).willReturn(true);

        service.delete(1L);

        then(repository).should().deleteById(1L);
    }

    @Test
    void anAuthorWithBooksCannotBeDeleted() {
        given(repository.existsById(1L)).willReturn(true);
        given(books.existsByAuthorsId(1L)).willReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Author 1 has books and cannot be deleted");
        then(repository).should(never()).deleteById(any());
    }

    @Test
    void booksOfAnAuthorComeFromAProjection() {
        given(repository.existsById(1L)).willReturn(true);
        BookTitleOnly dune = new BookTitleOnly() {
            @Override
            public Long getId() {
                return 3L;
            }

            @Override
            public String getTitle() {
                return "Dune";
            }
        };
        given(books.findByAuthorsIdOrderByTitle(1L)).willReturn(List.of(dune));

        assertThat(service.findBooks(1L)).containsExactly(new AuthorBookResponse(3L, "Dune"));
    }

    @Test
    void missingIdIsNotFound() {
        // The service throws a domain exception with no HTTP in it; GlobalExceptionHandler (§5.3)
        // decides that it means 404.
        given(repository.findById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Author 99 not found");
        assertThatThrownBy(() -> service.update(99L, new UpdateAuthorRequest("x", null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.findBooks(99L)).isInstanceOf(NotFoundException.class);
        then(repository).should(never()).deleteById(any());
    }
}
