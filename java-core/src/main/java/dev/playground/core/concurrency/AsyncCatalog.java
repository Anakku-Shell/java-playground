package dev.playground.core.concurrency;

import dev.playground.core.functional.BookCatalog;
import dev.playground.core.functional.BookSample;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * An async facade over {@link BookCatalog}: each lookup runs on the given executor and returns a
 * {@link CompletableFuture} at once, the way a client for a remote service would. Guide: §4.3
 * Concurrency.
 */
public final class AsyncCatalog {

    private static final Map<String, Integer> STOCK = Map.of("Dune", 3, "Emma", 1, "Cosmos", 2);

    private final BookCatalog catalog;
    private final Executor executor;

    public AsyncCatalog(BookCatalog catalog, Executor executor) {
        this.catalog = catalog;
        this.executor = executor;
    }

    /** Completes with the book, or fails with {@link NoSuchElementException} if there is none. */
    public CompletableFuture<BookSample> findAsync(String title) {
        return CompletableFuture.supplyAsync(
                () -> catalog.findByTitle(title)
                        .orElseThrow(() -> new NoSuchElementException("No book titled " + title)),
                executor);
    }

    /** Copies in stock; 0 for titles the stock list does not know. */
    public CompletableFuture<Integer> stockAsync(String title) {
        return CompletableFuture.supplyAsync(() -> STOCK.getOrDefault(title, 0), executor);
    }
}
