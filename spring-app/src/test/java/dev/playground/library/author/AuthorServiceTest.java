package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import dev.playground.library.common.NotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * A plain unit test: no Spring context at all. Constructor injection is what makes this possible:
 * {@code @InjectMocks} builds the service with {@code new}, passing a Mockito mock of the repository
 * interface. Guide: §5.2 REST API, §5.4 Persistence with JPA.
 */
@ExtendWith(MockitoExtension.class)
class AuthorServiceTest {

    @Mock
    private AuthorRepository repository;

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
    void findAllListsInIdOrder() {
        given(repository.findAll(Sort.by("id")))
                .willReturn(List.of(stored(1L, "Jane Austen", 1775), stored(2L, "Frank Herbert", 1920)));

        assertThat(service.findAll())
                .containsExactly(
                        new AuthorResponse(1L, "Jane Austen", 1775), new AuthorResponse(2L, "Frank Herbert", 1920));
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
    void deleteRemovesAnExistingAuthor() {
        given(repository.existsById(1L)).willReturn(true);

        service.delete(1L);

        then(repository).should().deleteById(1L);
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
        then(repository).should(never()).deleteById(any());
    }
}
