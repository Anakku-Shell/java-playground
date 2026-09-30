package dev.playground.core.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.playground.core.functional.BookCatalog;
import dev.playground.core.functional.BookSample;
import dev.playground.core.functional.BookSamples;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * {@link CompletableFuture}: a future you can chain, so the next step runs when the previous one
 * finishes instead of a thread blocking in {@code get()}. Java has no {@code async}/{@code await}:
 * you either chain callbacks or block at the end with {@code join()}. Guide: §4.3 Concurrency.
 */
@Timeout(10)
class CompletableFutureTest {

    private final ExecutorService pool = Executors.newFixedThreadPool(
            4, Thread.ofPlatform().name("catalog-", 0).factory());
    private final AsyncCatalog catalog = new AsyncCatalog(new BookCatalog(BookSamples.all()), pool);

    @AfterEach
    void closePool() {
        pool.close();
    }

    @Test
    void supplyAsyncRunsOnTheGivenExecutor() {
        // Without the executor argument the task runs on ForkJoinPool.commonPool() (or on a new
        // thread per task when that pool has fewer than 2 threads, as on a 1–2 CPU machine). The
        // common pool is sized for CPU work and shared by the whole JVM. Pass your own executor
        // for blocking work.
        CompletableFuture<String> threadName =
                CompletableFuture.supplyAsync(() -> Thread.currentThread().getName(), pool);

        assertThat(threadName.join()).startsWith("catalog-");
        assertThat(catalog.findAsync("Dune").join().author()).isEqualTo("Frank Herbert");
    }

    @Test
    void thenApplyTransformsTheResult() {
        // thenApply ↔ map: a plain function on the value once it arrives.
        CompletableFuture<Integer> pages = catalog.findAsync("Dune").thenApply(BookSample::pages);

        assertThat(pages.join()).isEqualTo(412);
    }

    @Test
    void thenComposeChainsAnotherAsyncCall() {
        // thenCompose ↔ flatMap: the next step itself returns a future. thenApply here would give
        // CompletableFuture<CompletableFuture<Integer>>.
        CompletableFuture<Integer> stock =
                catalog.findAsync("Dune").thenCompose(book -> catalog.stockAsync(book.title()));

        assertThat(stock.join()).isEqualTo(3);
    }

    @Test
    void thenCombineJoinsTwoIndependentResults() {
        // Both lookups start now and run in parallel; the function runs when both are done.
        CompletableFuture<String> summary = catalog.findAsync("Emma")
                .thenCombine(catalog.stockAsync("Emma"), (book, stock) -> book.title() + ": " + stock + " in stock");

        assertThat(summary.join()).isEqualTo("Emma: 1 in stock");
    }

    @Test
    void allOfWaitsForAllOfThem() {
        List<CompletableFuture<Integer>> stocks = List.of("Dune", "Emma", "Cosmos").stream()
                .map(catalog::stockAsync)
                .toList();

        // allOf returns CompletableFuture<Void>: it only signals "all done". Read the values from
        // the original futures afterwards (join no longer blocks at that point).
        int total = CompletableFuture.allOf(stocks.toArray(CompletableFuture[]::new))
                .thenApply(
                        _ -> stocks.stream().mapToInt(CompletableFuture::join).sum())
                .join();

        assertThat(total).isEqualTo(3 + 1 + 2);
    }

    @Test
    void exceptionallyRecoversFromAFailure() {
        CompletableFuture<String> title = catalog.findAsync("Ulysses")
                .thenApply(BookSample::title) // skipped, because the lookup fails
                .exceptionally(error -> "unknown");

        assertThat(title.join()).isEqualTo("unknown");
    }

    @Test
    void handleSeesEitherTheResultOrTheError() {
        CompletableFuture<String> found = catalog.findAsync("SPQR").handle(CompletableFutureTest::describe);
        CompletableFuture<String> missing = catalog.findAsync("Ulysses").handle(CompletableFutureTest::describe);

        assertThat(found.join()).isEqualTo("found SPQR");
        // An exception thrown inside a stage (supplyAsync, thenApply…) reaches handle, exceptionally
        // and later stages wrapped in a CompletionException. It arrives unwrapped only when the
        // future was failed directly with completeExceptionally (orTimeout, failedFuture).
        assertThat(missing.join()).isEqualTo("failed: CompletionException caused by NoSuchElementException");
    }

    @Test
    void joinVsGetWrapTheErrorDifferently() {
        CompletableFuture<BookSample> missing = catalog.findAsync("Ulysses");

        // join() throws the unchecked CompletionException; get() the checked ExecutionException
        // (the Future interface's contract). Both keep the real error as the cause.
        assertThatThrownBy(missing::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> missing.get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(NoSuchElementException.class);
    }

    @Test
    void cancelMarksTheFutureButDoesNotStopTheWork() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();

        CompletableFuture<String> work = CompletableFuture.supplyAsync(
                () -> {
                    started.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        interrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                    finished.countDown();
                    return "done";
                },
                pool);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        // Unlike Future.cancel(true) on an executor task, the flag has no effect here: the
        // future is completed with a CancellationException, but nothing interrupts the thread.
        assertThat(work.cancel(true)).isTrue();
        assertThatThrownBy(work::join).isInstanceOf(CancellationException.class);

        release.countDown();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(interrupted.get()).isFalse(); // the task ran to the end
    }

    @Test
    void orTimeoutFailsASlowFuture() {
        CompletableFuture<String> neverCompletes = new CompletableFuture<>();

        CompletableFuture<String> bounded = neverCompletes.orTimeout(50, TimeUnit.MILLISECONDS);

        assertThatThrownBy(bounded::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void completeOnTimeoutFallsBackToADefault() {
        CompletableFuture<Integer> neverCompletes = new CompletableFuture<>();

        assertThat(neverCompletes
                        .completeOnTimeout(0, 50, TimeUnit.MILLISECONDS)
                        .join())
                .isZero();
    }

    private static String describe(BookSample book, Throwable error) {
        if (error != null) {
            return "failed: " + error.getClass().getSimpleName() + " caused by "
                    + error.getCause().getClass().getSimpleName();
        }
        return "found " + book.title();
    }
}
