package dev.playground.library.author;

import dev.playground.library.author.dto.AuthorResponse;
import dev.playground.library.author.dto.CreateAuthorRequest;
import dev.playground.library.author.dto.UpdateAuthorRequest;

/**
 * Manual mapping between {@link Author} and its DTOs: plain static methods, no library. Easy to
 * read and to debug, and the compiler checks every field. Guide: §5.2 REST API.
 */
public final class AuthorMapper {

    private AuthorMapper() {}

    public static AuthorResponse toResponse(Author author) {
        return new AuthorResponse(author.getId(), author.getName(), author.getBirthYear());
    }

    public static Author toNewAuthor(CreateAuthorRequest request) {
        return new Author(request.name(), request.birthYear());
    }

    public static void apply(UpdateAuthorRequest request, Author author) {
        author.setName(request.name());
        author.setBirthYear(request.birthYear());
    }
}
