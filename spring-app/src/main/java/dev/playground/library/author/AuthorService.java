package dev.playground.library.author;

import dev.playground.library.author.dto.AuthorBookResponse;
import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import dev.playground.library.book.BookRepository;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.common.PageResponse;
import dev.playground.library.common.Paging;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Author use cases. Takes and returns DTOs, so the controller never touches {@link Author}. The
 * business rules and the transactions live here: read-only by default (class level), read-write on
 * the write methods (see BookService). Guide: §5.2 REST API, §5.4 Persistence with JPA, §5.5
 * Advanced JPA, §5.6 Transactions.
 */
@Service
@Transactional(readOnly = true)
public class AuthorService {

    private static final Set<String> SORTABLE = Set.of("id", "name", "birthYear");

    private final AuthorRepository repository;
    private final BookRepository books;

    public AuthorService(AuthorRepository repository, BookRepository books) {
        this.repository = repository;
        this.books = books;
    }

    public PageResponse<AuthorResponse> findAll(Pageable pageable) {
        return PageResponse.from(
                repository.findAll(Paging.sanitize(pageable, SORTABLE)).map(AuthorMapper::toResponse));
    }

    public AuthorResponse findById(Long id) {
        return AuthorMapper.toResponse(getOrThrow(id));
    }

    /** The author's books, id and title only: an interface projection, no book entities (§5.5). */
    public List<AuthorBookResponse> findBooks(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Author", id);
        }
        return books.findByAuthorsIdOrderByTitle(id).stream()
                .map(book -> new AuthorBookResponse(book.getId(), book.getTitle()))
                .toList();
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

    /**
     * An author who still has books stays: deleting them would leave books without their author.
     * The foreign key from book_authors refuses it too; the check turns that into a clear 409.
     */
    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new NotFoundException("Author", id);
        }
        if (books.existsByAuthorsId(id)) {
            throw new ConflictException("Author " + id + " has books and cannot be deleted");
        }
        repository.deleteById(id);
    }

    private Author getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Author", id));
    }
}
