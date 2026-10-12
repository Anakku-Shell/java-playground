package dev.playground.core.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Executors: you submit tasks, a pool of threads runs them. Creating a thread is expensive, so a
 * pool keeps a few and reuses them. Guide: §4.3 Concurrency.
 *
 * <p>Since Java 19 an {@link ExecutorService} is {@link AutoCloseable}: try-with-resources shuts
 * it down and waits for the submitted tasks. Before that you had to call {@code shutdown()} and
 * {@code awaitTermination(...)} yourself. Either way, a pool nobody shuts down keeps the JVM alive:
 * its threads are not daemon threads.
 */
@Timeout(10)
class ExecutorsTest {

    @Test
    void fixedPoolReusesAFewThreads() {
        Set<String> threadNames = ConcurrentHashMap.newKeySet();

        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            for (int i = 0; i < 20; i++) {
                pool.submit(() -> threadNames.add(Thread.currentThread().getName()));
            }
        } // close() waits for all 20 tasks

        // 20 tasks, at most 3 threads: the extra tasks waited in the pool's queue.
        assertThat(threadNames).hasSizeBetween(1, 3);
    }

    @Test
    void submitReturnsAFuture() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            // A Callable returns a value (a Runnable does not); submit wraps it in a Future.
            Callable<Integer> countPages = () -> 412 + 188;
            Future<Integer> pages = pool.submit(countPages);

            // get() blocks the calling thread until the result is there.
            assertThat(pages.get(5, TimeUnit.SECONDS)).isEqualTo(600);
            assertThat(pages.isDone()).isTrue();
        }
    }

    @Test
    void futureGetWrapsTheTaskExceptionInExecutionException() {
        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<Integer> failing = pool.submit(() -> {
                throw new IllegalStateException("no such shelf");
            });

            // The exception happened on another thread. get() rethrows it wrapped, so the
            // original is the cause.
            assertThatThrownBy(() -> failing.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .cause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("no such shelf");
        }
    }

    @Test
    void futureGetWithATimeout() throws InterruptedException {
        CountDownLatch neverOpened = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newSingleThreadExecutor()) {
            Future<String> slow = pool.submit(() -> {
                neverOpened.await(); // blocks until interrupted
                return "done";
            });

            assertThatThrownBy(() -> slow.get(50, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            // A timeout does not stop the task. cancel(true) interrupts it; without that, close()
            // below would wait for it forever.
            assertThat(slow.cancel(true)).isTrue();
            assertThat(slow.isCancelled()).isTrue();
        }
    }

    @Test
    void invokeAllWaitsForEveryTask() throws Exception {
        List<Callable<Integer>> tasks = IntStream.rangeClosed(1, 5)
                .<Callable<Integer>>mapToObj(n -> () -> n * n)
                .toList();

        try (ExecutorService pool = Executors.newFixedThreadPool(3)) {
            List<Future<Integer>> results = pool.invokeAll(tasks);

            // invokeAll returns when all tasks are done; the futures keep the order of the tasks.
            assertThat(results).allMatch(Future::isDone);
            assertThat(results.stream().map(Future::resultNow).toList()).containsExactly(1, 4, 9, 16, 25);
        }
    }

    @Test
    void closeWaitsForSubmittedTasks() {
        AtomicInteger finished = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try (pool) {
            for (int i = 0; i < 4; i++) {
                pool.submit(() -> {
                    Thread.sleep(20);
                    finished.incrementAndGet();
                    return null;
                });
            }
        }

        assertThat(finished.get()).isEqualTo(4);
        assertThat(pool.isTerminated()).isTrue();
    }

    @Test
    void shutdownRejectsNewTasks() {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        pool.shutdown(); // finishes queued work, accepts nothing new

        assertThatThrownBy(() -> pool.submit(() -> "late")).isInstanceOf(RejectedExecutionException.class);
        pool.close();
    }
}
