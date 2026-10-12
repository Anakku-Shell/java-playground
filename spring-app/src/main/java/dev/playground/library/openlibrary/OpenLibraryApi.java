package dev.playground.library.openlibrary;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Open Library's JSON API as a Java interface: an HTTP interface. There is no implementation to
 * write: Spring generates a proxy that turns each call into a request (method + URL from the
 * annotations, path variables filled in) and the JSON response into the return type. Like a
 * Spring Data repository, but for HTTP. {@link OpenLibraryConfig} registers it; the base URL and
 * timeouts come from {@code spring.http.serviceclient.openlibrary.*}.
 *
 * <p>Only {@link OpenLibraryClient} calls it, and the records below describe only the fields this
 * API reads: Jackson ignores the rest. Guide: §5.9 Beyond CRUD.
 */
@HttpExchange(accept = "application/json")
interface OpenLibraryApi {

    /** An edition by ISBN. Open Library answers with a redirect to the edition's own URL. */
    @GetExchange("/isbn/{isbn}.json")
    Edition edition(@PathVariable String isbn);

    /** An author by id: the last part of a key such as {@code /authors/OL79034A}. */
    @GetExchange("/authors/{id}.json")
    Author author(@PathVariable String id);

    record Edition(
            String title,
            List<Key> authors,
            @JsonProperty("publish_date") String publishDate) {}

    record Key(String key) {}

    record Author(String name, @JsonProperty("birth_date") String birthDate) {}
}
