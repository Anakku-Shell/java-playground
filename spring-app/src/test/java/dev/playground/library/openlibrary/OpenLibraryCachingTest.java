package dev.playground.library.openlibrary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import dev.playground.library.common.ExternalServiceException;
import dev.playground.library.config.CachingConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.boot.cache.test.autoconfigure.AutoConfigureCache;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * {@code @Cacheable} on {@link OpenLibraryClient#findBook}: a found book is asked for once, misses
 * and failures are asked again. Caching is a proxy around the bean, so the test needs a Spring
 * context: just the client, the real {@link CachingConfig} and the cache auto-configuration
 * ({@code @AutoConfigureCache}, which in tests defaults to a no-op cache: SIMPLE is the in-memory
 * one the application uses). The generated HTTP interface is replaced by a mock that counts calls.
 * Guide: §5.9 Beyond CRUD.
 */
@SpringBootTest(classes = {CachingConfig.class, OpenLibraryClient.class})
@AutoConfigureCache(cacheProvider = CacheType.SIMPLE)
class OpenLibraryCachingTest {

    private static final OpenLibraryApi.Edition DUNE = new OpenLibraryApi.Edition("Dune", List.of(), "1965");

    @MockitoBean
    private OpenLibraryApi api;

    @Autowired
    private OpenLibraryClient client;

    @Autowired
    private CacheManager caches;

    @BeforeEach
    void emptyTheCache() {
        // The context, and so the cache, is shared by the tests of this class.
        caches.getCache("open-library").clear();
    }

    @Test
    void theSecondLookupOfAnIsbnComesFromTheCache() {
        given(api.edition("9780441013593")).willReturn(DUNE);

        var first = client.findBook("9780441013593");
        var second = client.findBook("9780441013593");

        assertThat(second).isEqualTo(first).isPresent();
        then(api).should(times(1)).edition("9780441013593");
    }

    @Test
    void eachIsbnHasItsOwnEntry() {
        given(api.edition("9780441013593")).willReturn(DUNE);
        given(api.edition("9780441172719")).willReturn(new OpenLibraryApi.Edition("Dune Messiah", List.of(), null));

        client.findBook("9780441013593");
        client.findBook("9780441172719");

        then(api).should().edition("9780441013593");
        then(api).should().edition("9780441172719");
    }

    @Test
    void aBookOpenLibraryDoesNotKnowIsAskedForAgain() {
        // Not cached ("unless"): Open Library adds books, and an import may succeed tomorrow.
        given(api.edition("9780441013593"))
                .willThrow(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "", null, null, null));

        assertThat(client.findBook("9780441013593")).isEmpty();
        assertThat(client.findBook("9780441013593")).isEmpty();
        then(api).should(times(2)).edition("9780441013593");
    }

    @Test
    void aFailureIsNotCached() {
        // An exception leaves the method without a result, so there is nothing to cache.
        given(api.edition("9780441013593"))
                .willThrow(new ResourceAccessException("Read timed out"))
                .willReturn(DUNE);

        assertThatThrownBy(() -> client.findBook("9780441013593")).isInstanceOf(ExternalServiceException.class);
        assertThat(client.findBook("9780441013593")).isPresent();
    }
}
