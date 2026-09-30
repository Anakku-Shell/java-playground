package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import dev.playground.library.common.NotFoundException;
import org.junit.jupiter.api.Test;

/**
 * A plain unit test: no Spring context at all. Constructor injection is what makes this possible:
 * the test builds the service with {@code new}, passing a real in-memory repository. Guide: §5.2
 * REST API.
 */
class AuthorServiceTest {

    private final AuthorService service = new AuthorService(new AuthorRepository());

    @Test
    void createAssignsAnId() {
        AuthorResponse created = service.create(new CreateAuthorRequest("Ursula K. Le Guin", 1929));

        assertThat(created.id()).isNotNull();
        assertThat(created).isEqualTo(new AuthorResponse(created.id(), "Ursula K. Le Guin", 1929));
    }

    @Test
    void findAllListsInIdOrder() {
        AuthorResponse first = service.create(new CreateAuthorRequest("Jane Austen", 1775));
        AuthorResponse second = service.create(new CreateAuthorRequest("Frank Herbert", 1920));

        assertThat(service.findAll()).containsExactly(first, second);
    }

    @Test
    void updateChangesTheFields() {
        AuthorResponse created = service.create(new CreateAuthorRequest("Carl Sagn", null));

        AuthorResponse updated = service.update(created.id(), new UpdateAuthorRequest("Carl Sagan", 1934));

        assertThat(updated).isEqualTo(new AuthorResponse(created.id(), "Carl Sagan", 1934));
        assertThat(service.findById(created.id())).isEqualTo(updated);
    }

    @Test
    void deleteRemovesTheAuthor() {
        AuthorResponse created = service.create(new CreateAuthorRequest("Mary Beard", 1955));

        service.delete(created.id());

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void missingIdIsNotFound() {
        // The service throws a domain exception with no HTTP in it; GlobalExceptionHandler (§5.3)
        // decides that it means 404.
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Author 99 not found");
        assertThatThrownBy(() -> service.update(99L, new UpdateAuthorRequest("x", null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(NotFoundException.class);
    }
}
