package dev.playground.library.author;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * HTTP only: maps requests to service calls and picks the status codes. No business rules here.
 * {@code @Valid} checks a request body against its record's constraints before the method runs;
 * {@code @Positive} on a path variable turns on Spring's built-in method validation (§5.3).
 * Errors become ProblemDetails in {@code GlobalExceptionHandler}. Guide: §5.2 REST API.
 */
@RestController
@RequestMapping("/api/authors")
public class AuthorController {

    private final AuthorService service;

    public AuthorController(AuthorService service) {
        this.service = service;
    }

    @GetMapping
    public List<AuthorResponse> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AuthorResponse get(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    /** 201 Created with a Location header pointing at the new resource. */
    @PostMapping
    public ResponseEntity<AuthorResponse> create(@Valid @RequestBody CreateAuthorRequest request) {
        AuthorResponse created = service.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    public AuthorResponse update(@PathVariable @Positive Long id, @Valid @RequestBody UpdateAuthorRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable @Positive Long id) {
        service.delete(id);
    }
}
