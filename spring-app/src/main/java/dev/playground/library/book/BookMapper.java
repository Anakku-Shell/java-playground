package dev.playground.library.book;

import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import java.util.List;

/**
 * Manual mapping between {@link Book} and its DTOs. ISBNs are stored as 13 digits whatever form the
 * request used (§5.3). Guide: §5.2 REST API.
 */
public final class BookMapper {

    private BookMapper() {}

    public static BookResponse toResponse(Book book) {
        // No loans yet (§5.5), so every copy is available; no author relation yet either.
        return new BookResponse(
                book.getId(),
                book.getIsbn(),
                book.getTitle(),
                book.getPublishedYear(),
                book.getTotalCopies(),
                book.getTotalCopies(),
                List.of());
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
