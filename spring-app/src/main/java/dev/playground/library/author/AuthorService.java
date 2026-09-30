package dev.playground.library.author;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import dev.playground.library.common.NotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Author use cases. Takes and returns DTOs, so the controller never touches {@link Author}. The
 * business rules and (from §5.6) the transactions live here. Guide: §5.2 REST API.
 */
@Service
public class AuthorService {

    private final AuthorRepository repository;

    public AuthorService(AuthorRepository repository) {
        this.repository = repository;
    }

    public List<AuthorResponse> findAll() {
        return repository.findAll().stream().map(AuthorMapper::toResponse).toList();
    }

    public AuthorResponse findById(Long id) {
        return AuthorMapper.toResponse(getOrThrow(id));
    }

    public AuthorResponse create(CreateAuthorRequest request) {
        return AuthorMapper.toResponse(repository.save(AuthorMapper.toNewAuthor(request)));
    }

    public AuthorResponse update(Long id, UpdateAuthorRequest request) {
        Author author = getOrThrow(id);
        AuthorMapper.apply(request, author);
        return AuthorMapper.toResponse(repository.save(author));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Author", id);
        }
        repository.deleteById(id);
    }

    private Author getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Author", id));
    }
}
