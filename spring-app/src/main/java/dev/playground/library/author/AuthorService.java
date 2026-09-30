package dev.playground.library.author;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import dev.playground.library.common.NotFoundException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Author use cases. Takes and returns DTOs, so the controller never touches {@link Author}. The
 * business rules and the transactions live here. Guide: §5.2 REST API, §5.4 Persistence with JPA.
 */
@Service
public class AuthorService {

    private final AuthorRepository repository;

    public AuthorService(AuthorRepository repository) {
        this.repository = repository;
    }

    public List<AuthorResponse> findAll() {
        return repository.findAll(Sort.by("id")).stream()
                .map(AuthorMapper::toResponse)
                .toList();
    }

    public AuthorResponse findById(Long id) {
        return AuthorMapper.toResponse(getOrThrow(id));
    }

    @Transactional
    public AuthorResponse create(CreateAuthorRequest request) {
        return AuthorMapper.toResponse(repository.save(AuthorMapper.toNewAuthor(request)));
    }

    /** No {@code save()}: dirty checking writes the managed author on commit (see BookService). */
    @Transactional
    public AuthorResponse update(Long id, UpdateAuthorRequest request) {
        Author author = getOrThrow(id);
        AuthorMapper.apply(request, author);
        return AuthorMapper.toResponse(author);
    }

    @Transactional
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
