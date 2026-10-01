package dev.playground.library.openlibrary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.library.common.ExternalServiceException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.AutoConfigureMockRestServiceServer;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@link OpenLibraryClient} against a real local HTTP server ({@link OpenLibraryStub}), through the
 * same Spring wiring as the application: the HTTP service group from {@link OpenLibraryConfig}, with
 * its base URL and read timeout from {@code spring.http.serviceclient.openlibrary.*}.
 *
 * <p>{@code @RestClientTest} is the slice for HTTP clients: it loads the named component and the
 * RestClient auto-configuration, nothing else (no database). Two changes to it: the HTTP service
 * client auto-configuration, which the slice does not include yet, and no
 * {@code MockRestServiceServer}, since the stub is a real server. Guide: §5.9 Beyond CRUD.
 */
@RestClientTest(OpenLibraryClient.class)
@Import(OpenLibraryConfig.class)
@ImportAutoConfiguration({HttpServiceClientPropertiesAutoConfiguration.class, HttpServiceClientAutoConfiguration.class})
@AutoConfigureMockRestServiceServer(enabled = false)
class OpenLibraryClientTest {

    private static final OpenLibraryStub openLibrary = OpenLibraryStub.start();

    private static final String DUNE_EDITION = """
            {"title": "Dune", "authors": [{"key": "/authors/OL79034A"}], "publish_date": "August 2, 2005",
             "isbn_13": ["9780441013593"], "number_of_pages": 544}""";

    @Autowired
    private OpenLibraryClient client;

    // Resolved when the context starts, after the static stub has its port.
    @DynamicPropertySource
    static void pointAtTheStub(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.openlibrary.base-url", openLibrary::baseUrl);
        registry.add("spring.http.serviceclient.openlibrary.read-timeout", () -> "300ms");
    }

    @AfterAll
    static void stopTheStub() {
        openLibrary.close();
    }

    @BeforeEach
    void resetTheStub() {
        openLibrary.reset();
    }

    @Test
    void findsTheEditionAndItsAuthors() {
        // What openlibrary.org does: the ISBN URL redirects to the edition's own URL.
        openLibrary.redirect("/isbn/9780441013593.json", "/books/OL7524304M.json");
        openLibrary.json("/books/OL7524304M.json", DUNE_EDITION);
        openLibrary.json("/authors/OL79034A.json", """
                {"name": "Frank Herbert", "birth_date": "8 October 1920", "key": "/authors/OL79034A"}""");

        assertThat(client.findBook("9780441013593"))
                .contains(new OpenLibraryBook(
                        "9780441013593", "Dune", 2005, List.of(new OpenLibraryBook.Author("Frank Herbert", 1920))));
    }

    @Test
    void anEditionWithoutAuthorsHasNone() {
        openLibrary.json("/isbn/9780441013593.json", """
                {"title": "Dune"}""");

        assertThat(client.findBook("9780441013593"))
                .contains(new OpenLibraryBook("9780441013593", "Dune", null, List.of()));
    }

    @Test
    void anEditionWithoutATitleIsAnExternalServiceFailure() {
        // A book needs a title (NOT NULL); a record without one is not something we can import.
        openLibrary.json("/isbn/9780441013593.json", """
                {"publish_date": "2005"}""");

        assertThatThrownBy(() -> client.findBook("9780441013593")).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void anEmptyAnswerIsAnExternalServiceFailure() {
        openLibrary.status("/isbn/9780441013593.json", 200);

        assertThatThrownBy(() -> client.findBook("9780441013593")).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void authorsWithoutAKeyOrANameAreLeftOut() {
        // Open Library merges duplicate authors: the old record then answers with a redirect record
        // (no name). One unusable author should not block the import of the book.
        openLibrary.json("/isbn/9780441013593.json", """
                {"title": "Dune", "authors": [{"key": "/authors/OL1A"}, {"key": "/authors/OL2A"}, {}]}""");
        openLibrary.json("/authors/OL1A.json", """
                {"type": {"key": "/type/redirect"}, "location": "/authors/OL79034A"}""");
        openLibrary.json("/authors/OL2A.json", """
                {"name": "Frank Herbert"}""");

        assertThat(client.findBook("9780441013593"))
                .map(OpenLibraryBook::authors)
                .contains(List.of(new OpenLibraryBook.Author("Frank Herbert", null)));
    }

    @Test
    void anUnknownIsbnIsEmpty() {
        // No route: the stub answers 404, as Open Library does for an ISBN it has never seen.
        assertThat(client.findBook("9780441013593")).isEmpty();
    }

    @Test
    void aServerErrorIsAnExternalServiceFailure() {
        openLibrary.status("/isbn/9780441013593.json", 503);

        assertThatThrownBy(() -> client.findBook("9780441013593"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Open Library is unavailable");
    }

    @Test
    void anAuthorThatCannotBeReadIsAnExternalServiceFailure() {
        // The edition exists, so its author must too: a 404 here is Open Library's problem, not
        // "no such book".
        openLibrary.json("/isbn/9780441013593.json", DUNE_EDITION);

        assertThatThrownBy(() -> client.findBook("9780441013593")).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void anHtmlPageInsteadOfJsonIsAnExternalServiceFailure() {
        // A maintenance page or a proxy error can answer 200 with HTML.
        openLibrary.html("/isbn/9780441013593.json", "<html><body>Down for maintenance</body></html>");

        assertThatThrownBy(() -> client.findBook("9780441013593")).isInstanceOf(ExternalServiceException.class);
    }

    /**
     * A server that accepts the connection and then never answers would hang the request thread
     * forever without a read timeout. With one (300 ms here, 5 s in application.yml) the call fails
     * fast, and GlobalExceptionHandler turns the exception into a 502 (BookControllerTest).
     */
    @Test
    void timeoutMapsTo502() {
        openLibrary.slow("/isbn/9780441013593.json", Duration.ofSeconds(3), DUNE_EDITION);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.findBook("9780441013593"))
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("Open Library is unavailable");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(
            nullValues = "null",
            value = {
                "'August 2, 2005', 2005",
                "1965,             1965",
                "8 October 1920,   1920",
                "'c. 1965?',       1965",
                "'May 5',          null",
                "'',               null",
                "null,             null"
            })
    void readsTheYearOutOfAFreeTextDate(String date, Integer year) {
        assertThat(OpenLibraryClient.yearIn(date)).isEqualTo(year);
    }
}
