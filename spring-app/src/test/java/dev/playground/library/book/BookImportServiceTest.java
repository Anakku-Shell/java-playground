package dev.playground.library.book;

import static dev.playground.library.testing.TestDataFactory.herbert;
import static dev.playground.library.testing.TestDataFactory.withId;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

import dev.playground.library.author.Author;
import dev.playground.library.author.AuthorRepository;
import dev.playground.library.book.dto.AuthorSummary;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.common.ConflictException;
import dev.playground.library.common.NotFoundException;
import dev.playground.library.openlibrary.OpenLibraryBook;
import dev.playground.library.openlibrary.OpenLibraryClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Importing a book from Open Library, with the client and the repositories mocked. The
 * {@code TransactionTemplate} is real, over a mocked transaction manager: enough to see when the
 * transaction starts. Guide: §5.9 Beyond CRUD.
 */
@ExtendWith(MockitoExtension.class)
class BookImportServiceTest {

    private static final OpenLibraryBook DUNE = new OpenLibraryBook(
            "9780441013593", "Dune", 2005, List.of(new OpenLibraryBook.Author("Frank Herbert", 1920)));

    @Mock
    private OpenLibraryClient openLibrary;

    @Mock
    private BookRepository books;

    @Mock
    private AuthorRepository authors;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Captor
    private ArgumentCaptor<Book> savedBook;

    private BookImportService service;

    @BeforeEach
    void setUp() {
        service = new BookImportService(openLibrary, books, authors, new TransactionTemplate(transactionManager));
    }

    private void bookIsSaved() {
        given(books.save(savedBook.capture())).willAnswer(invocation -> withId(invocation.getArgument(0), 10L));
    }

    @Test
    void importsTheBookWithOneCopyAndCreatesItsAuthor() {
        given(openLibrary.findBook("9780441013593")).willReturn(Optional.of(DUNE));
        given(authors.findFirstByNameIgnoreCaseOrderByIdAsc("Frank Herbert")).willReturn(Optional.empty());
        given(authors.save(any(Author.class))).willAnswer(invocation -> withId(invocation.getArgument(0), 3L));
        bookIsSaved();

        BookResponse imported = service.importBook("9780441013593");

        assertThat(imported)
                .isEqualTo(new BookResponse(
                        10L, "9780441013593", "Dune", 2005, 1, 1, List.of(new AuthorSummary(3L, "Frank Herbert"))));
        assertThat(savedBook.getValue().getAuthors())
                .singleElement()
                .satisfies(author -> assertThat(author.getBirthYear()).isEqualTo(1920));
    }

    @Test
    void reusesAnAuthorWithTheSameName() {
        given(openLibrary.findBook("9780441013593")).willReturn(Optional.of(DUNE));
        given(authors.findFirstByNameIgnoreCaseOrderByIdAsc("Frank Herbert"))
                .willReturn(Optional.of(withId(herbert(), 1L)));
        bookIsSaved();

        BookResponse imported = service.importBook("9780441013593");

        assertThat(imported.authors()).containsExactly(new AuthorSummary(1L, "Frank Herbert"));
        then(authors).should(never()).save(any());
    }

    @Test
    void acceptsAnIsbn10WithHyphens() {
        given(openLibrary.findBook("9780441013593")).willReturn(Optional.of(DUNE));
        given(authors.findFirstByNameIgnoreCaseOrderByIdAsc("Frank Herbert"))
                .willReturn(Optional.of(withId(herbert(), 1L)));
        bookIsSaved();

        assertThat(service.importBook("0-441-01359-7").isbn()).isEqualTo("9780441013593");
    }

    @Test
    void cutsATitleLongerThanTheColumn() {
        String longTitle = "A".repeat(400);
        given(openLibrary.findBook("9780441013593"))
                .willReturn(Optional.of(new OpenLibraryBook("9780441013593", longTitle, null, List.of())));
        bookIsSaved();

        assertThat(service.importBook("9780441013593").title()).hasSize(300);
    }

    @Test
    void aDuplicateIsbnIsAConflictWithoutAskingOpenLibrary() {
        given(books.existsByIsbn("9780441013593")).willReturn(true);

        assertThatThrownBy(() -> service.importBook("9780441013593"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("A book with ISBN 9780441013593 already exists");
        verifyNoInteractions(openLibrary);
    }

    @Test
    void anIsbnOpenLibraryDoesNotKnowIsNotFound() {
        given(openLibrary.findBook("9780441013593")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.importBook("9780441013593"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Open Library book 9780441013593 not found");
        verifyNoInteractions(transactionManager);
    }

    /**
     * The remote call is the slow part. Inside a transaction it would hold a database connection for
     * as long as Open Library takes (a read timeout per request, one request per author), and a few
     * slow imports would empty the connection pool for everyone. So: ask first, then open a short
     * transaction to write.
     */
    @Test
    void asksOpenLibraryBeforeOpeningTheTransaction() {
        given(openLibrary.findBook("9780441013593")).willReturn(Optional.of(DUNE));
        given(authors.findFirstByNameIgnoreCaseOrderByIdAsc("Frank Herbert"))
                .willReturn(Optional.of(withId(herbert(), 1L)));
        bookIsSaved();

        service.importBook("9780441013593");

        InOrder order = inOrder(openLibrary, transactionManager, books);
        order.verify(openLibrary).findBook("9780441013593");
        order.verify(transactionManager).getTransaction(any());
        order.verify(books).save(any());
        order.verify(transactionManager).commit(any());
    }

    @Test
    void previewShowsWhatAnImportWouldCreateWithoutSavingIt() {
        given(openLibrary.findBook("9780441013593")).willReturn(Optional.of(DUNE));

        assertThat(service.preview("978-0-441-01359-3")).isEqualTo(DUNE);
        verifyNoInteractions(books, authors, transactionManager);
    }

    @Test
    void previewOfAnUnknownIsbnIsNotFound() {
        given(openLibrary.findBook("9780441013593")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview("9780441013593")).isInstanceOf(NotFoundException.class);
    }
}
