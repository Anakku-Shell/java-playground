package dev.playground.library.book;

import static java.util.stream.Collectors.toSet;

import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.openlibrary.OpenLibraryBook;
import dev.playground.library.openlibrary.OpenLibraryClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adds a book to the catalogue from Open Library: its title, year and authors, with one copy.
 *
 * <p>No {@code @Transactional} here, on purpose. The remote call can take seconds (a read timeout
 * per request: one for the edition, one per author), and a transaction holds a database connection
 * from its first query to its commit: a few slow imports would take every connection in the pool.
 * So the remote call runs first, outside any transaction, and {@link TransactionTemplate} then wraps
 * only the writes: programmatic transactions, for when the boundary sits in the middle of a method.
 * Guide: §5.9 Beyond CRUD, §5.6 Transactions.
 */
@Service
public class BookImportService {

    private static final int TITLE_MAX = 300; // books.title (V1)
    private static final int NAME_MAX = 200; // authors.name (V1)

    private final OpenLibraryClient openLibrary;
    private final BookRepository books;
    private final AuthorRepository authors;
    private final TransactionTemplate transaction;

    public BookImportService(
            OpenLibraryClient openLibrary,
            BookRepository books,
            AuthorRepository authors,
            TransactionTemplate transaction) {
        this.openLibrary = openLibrary;
        this.books = books;
        this.authors = authors;
        this.transaction = transaction;
    }

    /** What an import of this ISBN would bring in, without saving anything. */
    public OpenLibraryBook preview(String isbn) {
        String isbn13 = Isbn.toIsbn13(isbn);
        return find(isbn13);
    }

    /**
     * Imports the book with this ISBN. A duplicate is checked before calling Open Library (no
     * remote call for a 409); two imports of the same ISBN at once both pass that check, and the
     * unique constraint then turns the second into a 409 as well (GlobalExceptionHandler).
     */
    public BookResponse importBook(String isbn) {
        String isbn13 = Isbn.toIsbn13(isbn);
        if (books.existsByIsbn(isbn13)) {
            throw new ConflictException("A book with ISBN " + isbn13 + " already exists");
        }
        OpenLibraryBook found = find(isbn13);
        // One short read-write transaction; execute returns what the callback returns.
        return transaction.execute(status -> save(found));
    }

    private OpenLibraryBook find(String isbn13) {
        return openLibrary.findBook(isbn13).orElseThrow(() -> new NotFoundException("Open Library book", isbn13));
    }

    private BookResponse save(OpenLibraryBook found) {
        Book book = new Book(found.isbn(), fit(found.title(), TITLE_MAX), found.publishedYear(), 1);
        book.replaceAuthors(found.authors().stream().map(this::findOrCreate).collect(toSet()));
        return BookMapper.toResponse(books.save(book), 0);
    }

    /**
     * By name: Open Library's author ids are not stored here, so "Frank Herbert" in the catalogue is
     * taken to be Open Library's Frank Herbert. Good enough for a playground; a real catalogue would
     * store the external id (a column and a migration) and match on it.
     */
    private Author findOrCreate(OpenLibraryBook.Author author) {
        String name = fit(author.name(), NAME_MAX);
        return authors.findFirstByNameIgnoreCaseOrderByIdAsc(name)
                .orElseGet(() -> authors.save(new Author(name, author.birthYear())));
    }

    // Open Library has no length limits; our columns do. Cut rather than fail the import.
    private static String fit(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
