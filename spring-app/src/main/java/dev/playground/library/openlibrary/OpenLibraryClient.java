package dev.playground.library.openlibrary;

import dev.playground.library.common.ExternalServiceException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * What the rest of the application uses to ask Open Library about a book. It wraps the generated
 * {@link OpenLibraryApi} to decide what each failure means here:
 * <ul>
 *   <li>a 404 for the ISBN: Open Library has no such book, so {@code Optional.empty()};
 *   <li>anything else (a timeout, a refused connection, a 5xx, a body that is not the expected
 *       JSON): {@link ExternalServiceException}, which the API answers with 502 Bad Gateway.
 * </ul>
 * RestClient throws {@code HttpClientErrorException} for 4xx, {@code HttpServerErrorException}
 * for 5xx and {@code ResourceAccessException} for I/O errors and timeouts; all of them are
 * {@code RestClientException}s. Guide: §5.9 Beyond CRUD.
 */
@Component
public class OpenLibraryClient {

    private static final Logger log = LoggerFactory.getLogger(OpenLibraryClient.class);
    private static final String SERVICE = "Open Library";
    // Four digits standing alone: "August 2, 2005" -> 2005, "8 October 1920" -> 1920.
    private static final Pattern YEAR = Pattern.compile("\\b(\\d{4})\\b");

    private final OpenLibraryApi api;

    OpenLibraryClient(OpenLibraryApi api) {
        this.api = api;
    }

    /**
     * The edition with this ISBN (13 digits) and its authors: one request, plus one per author.
     *
     * <p>Cached by ISBN in "open-library" (§5.9): the first call goes to Open Library, later ones with
     * the same ISBN return the stored result without running this method. For an {@code Optional}
     * the cache stores what is inside, so {@code #result} is the book or null: a miss is not cached
     * (Open Library may add the book later), and neither is an exception.
     */
    @Cacheable(cacheNames = "open-library", unless = "#result == null")
    public Optional<OpenLibraryBook> findBook(String isbn13) {
        try {
            OpenLibraryApi.Edition edition;
            try {
                edition = api.edition(isbn13);
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            }
            // An empty 200 reads as null. Neither it nor a book without a title is "no such book": a
            // record we cannot use, answered like any other bad response, with a 502.
            if (edition == null || edition.title() == null || edition.title().isBlank()) {
                throw new ExternalServiceException(
                        SERVICE, new IllegalStateException("Edition " + isbn13 + " has no title"));
            }
            List<OpenLibraryBook.Author> authors = edition.authors() == null
                    ? List.of()
                    : edition.authors().stream()
                            .map(this::author)
                            .flatMap(Optional::stream)
                            .toList();
            return Optional.of(new OpenLibraryBook(isbn13, edition.title(), yearIn(edition.publishDate()), authors));
        } catch (RestClientException e) {
            // A 404 for an author lands here too: the edition names it, so it should exist.
            throw new ExternalServiceException(SERVICE, e);
        }
    }

    /**
     * An author with a name, or empty. Open Library merges duplicate authors, and the old record then
     * answers with a redirect record that has no name: one unusable author is left out (and logged)
     * rather than blocking the import of the whole book.
     */
    private Optional<OpenLibraryBook.Author> author(OpenLibraryApi.Key key) {
        if (key.key() == null || key.key().isBlank()) {
            log.warn("Open Library listed an author without a key; left out");
            return Optional.empty();
        }
        // "/authors/OL79034A" -> "OL79034A": as a path variable the slashes would be encoded.
        String id = key.key().substring(key.key().lastIndexOf('/') + 1);
        OpenLibraryApi.Author author = api.author(id);
        if (author == null || author.name() == null || author.name().isBlank()) {
            log.warn("Open Library author {} has no name; left out", id);
            return Optional.empty();
        }
        return Optional.of(new OpenLibraryBook.Author(author.name(), yearIn(author.birthDate())));
    }

    /** The first year in a free-text date, or null. Package-private for its test. */
    static Integer yearIn(String date) {
        if (date == null) {
            return null;
        }
        Matcher matcher = YEAR.matcher(date);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }
}
