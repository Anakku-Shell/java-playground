package dev.playground.library.book;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.book.validation.ValidIsbn;
import dev.playground.library.common.PageResponse;
import dev.playground.library.openlibrary.OpenLibraryBook;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP only, like {@code AuthorController}: maps requests to service calls and picks the status
 * codes. Guide: §5.2 REST API, §5.9 Beyond CRUD (import).
 */
@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookService service;
    private final BookImportService importService;

    public BookController(BookService service, BookImportService importService) {
        this.service = service;
        this.importService = importService;
    }

    /**
     * One page of books. Spring builds the {@code Pageable} from {@code ?page=} (zero-based),
     * {@code ?size=} (default 20, capped at 100 by {@code spring.data.web.pageable.max-page-size})
     * and {@code ?sort=title,desc} (repeatable); {@code @SortDefault} sorts by title when no sort
     * is given ({@code @PageableDefault} would also reset the size to its own default, 10).
     * {@code ?title=} and {@code ?authorId=} are optional filters.
     */
    @GetMapping
    public PageResponse<BookResponse> list(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) @Positive Long authorId,
            @SortDefault("title") Pageable pageable) {
        return service.findAll(title, authorId, pageable);
    }

    @GetMapping("/{id}")
    public BookResponse get(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    /** 201 Created with a Location header pointing at the new resource. */
    @PostMapping
    public ResponseEntity<BookResponse> create(@Valid @RequestBody CreateBookRequest request) {
        BookResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public BookResponse update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateBookRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long id) {
        service.delete(id);
    }

    /** What {@code POST} would import from Open Library, without saving it (§5.9). */
    @GetMapping("/import/{isbn}")
    public OpenLibraryBook previewImport(@PathVariable @ValidIsbn String isbn) {
        return importService.preview(isbn);
    }

    /**
     * Adds the book with this ISBN from Open Library, with one copy (§5.9). 404 when Open Library
     * does not know it, 502 when it cannot be reached in time.
     */
    @PostMapping("/import/{isbn}")
    public ResponseEntity<BookResponse> importBook(@PathVariable @ValidIsbn String isbn) {
        BookResponse created = importService.importBook(isbn);
        // The new book's own URL, not one under /import (fromCurrentRequest would build that).
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/books/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }
}
