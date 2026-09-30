package dev.playground.library.book;

import dev.playground.library.author.Author;
import dev.playground.library.book.dto.AuthorSummary;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import java.util.Comparator;

/**
 * Manual mapping between {@link Book} and its DTOs. ISBNs are stored as 13 digits whatever form the
 * request used (§5.3). The authors are set by the service, which has to look them up.
 * Guide: §5.2 REST API, §5.5 Advanced JPA.
 */
public final class BookMapper {

    private static final Comparator<Author> BY_NAME =
            Comparator.comparing(Author::getName).thenComparing(Author::getId);

    private BookMapper() {}

    /**
     * {@code activeLoans} comes from a query, not from the entity: a book does not hold its loans.
     * Touches the lazy {@code authors}, so it runs inside the service's transaction.
     */
    public static BookResponse toResponse(Book book, long activeLoans) {
        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getPublishedYear(),
                book.getTotalCopies(),
                (int) (book.getTotalCopies() - activeLoans),
                book.getAuthors().stream()
                        .sorted(BY_NAME) // a Set has no order; the JSON should
                        .map(author -> new AuthorSummary(author.getId(), author.getName()))
                        .toList());
    }

    public static Book toNewBook(CreateBookRequest request) {
        return new Book(Isbn.toIsbn13(request.isbn()), request.title(), request.publishedYear(), request.totalCopies());
    }

    public static void apply(UpdateBookRequest request, Book book) {
        book.setIsbn(Isbn.toIsbn13(request.isbn()));
        book.setTitle(request.title());
        book.setPublishedYear(request.publishedYear());
        book.setTotalCopies(request.totalCopies());
    }
}
