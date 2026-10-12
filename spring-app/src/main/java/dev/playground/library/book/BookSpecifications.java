package dev.playground.library.book;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.EscapeCharacter;

/**
 * The optional filters of {@code GET /api/books}, as Specifications: small query conditions built
 * with the JPA Criteria API and combined at runtime ({@code Specification.allOf}), so four filter
 * combinations do not need four repository methods. A filter that was not given is
 * {@code unrestricted()}, which adds no condition. Guide: §5.5 Advanced JPA.
 */
public final class BookSpecifications {

    private BookSpecifications() {}

    /** Title contains {@code title}, any case. */
    public static Specification<Book> titleContains(String title) {
        if (title == null || title.isBlank()) {
            return Specification.unrestricted();
        }
        // A derived "Containing" query escapes LIKE wildcards for you; hand-written criteria must,
        // or "%" in the filter would match every title.
        EscapeCharacter escape = EscapeCharacter.DEFAULT;
        String pattern = "%" + escape.escape(title) + "%";
        // upper() on both sides, in the database: Java and PostgreSQL fold some letters differently
        // (Java turns "ß" into "SS", PostgreSQL keeps it). The literal is sent as a bind parameter.
        return (book, query, cb) ->
                cb.like(cb.upper(book.get("title")), cb.upper(cb.literal(pattern)), escape.getEscapeCharacter());
    }

    /** One of the book's authors has this id: an inner join on book_authors. */
    public static Specification<Book> hasAuthor(Long authorId) {
        if (authorId == null) {
            return Specification.unrestricted();
        }
        return (book, query, cb) -> cb.equal(book.join("authors").get("id"), authorId);
    }
}
