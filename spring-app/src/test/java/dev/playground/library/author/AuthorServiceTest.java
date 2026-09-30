package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

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
    void missingIdIs404() {
        // For now the service throws Spring's ResponseStatusException; §5.3 replaces it with a
        // domain NotFoundException mapped to a ProblemDetail in one place.
        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND))
                .hasMessageContaining("Author 99 not found");
        assertThatThrownBy(() -> service.update(99L, new UpdateAuthorRequest("x", null)))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResponseStatusException.class);
    }
}
