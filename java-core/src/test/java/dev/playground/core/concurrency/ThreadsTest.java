package dev.playground.core.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Plain threads, a race condition and three ways to fix it. Guide: §4.3 Concurrency.
 *
 * <p>In real code you rarely create a {@link Thread} yourself: you hand tasks to an executor
 * (see {@link ExecutorsTest}). The raw API is here because everything else is built on it.
 */
@Timeout(10) // a concurrency bug should fail the test, not hang the build
class ThreadsTest {

    private static final int THREADS = 8;
    private static final int INCREMENTS_EACH = 100_000;

    @Test
    void startRunsTheRunnableOnANewThread() throws InterruptedException {
        AtomicReference<String> ranOn = new AtomicReference<>();
        Runnable task = () -> ranOn.set(Thread.currentThread().getName());

        Thread worker = new Thread(task, "worker-1");
        worker.start(); // returns at once; the task runs on the new thread
        worker.join(); // waits until the thread finishes

        assertThat(ranOn.get()).isEqualTo("worker-1");
        assertThat(worker.isAlive()).isFalse();
    }

    @Test
    void runInsteadOfStartStaysOnTheCallingThread() {
        AtomicReference<String> ranOn = new AtomicReference<>();
        Thread worker = new Thread(() -> ranOn.set(Thread.currentThread().getName()), "worker-2");

        // run() is an ordinary method call: no new thread, no concurrency. A common slip.
        worker.run();

        assertThat(ranOn.get()).isEqualTo(Thread.currentThread().getName());
    }

    // To run it anyway: ./mvnw -pl java-core test -Dtest='ThreadsTest#racyCounterLosesUpdates'
    //   -Djunit.jupiter.conditions.deactivate='org.junit.*DisabledCondition'
    @Test
    @Disabled("demonstration: flaky by nature. Enable it and run it a few times to see updates get lost")
    void racyCounterLosesUpdates() throws InterruptedException {
        UnsafeCounter counter = new UnsafeCounter();

        Hammer.hammer(counter, THREADS, INCREMENTS_EACH);

        // count++ is read, add, write. Two threads read the same value, both write value + 1, and
        // one increment is lost. The final value is usually well below 800 000, but not always,
        // which is exactly why it cannot be a reliable test.
        assertThat(counter.value()).isLessThan(THREADS * INCREMENTS_EACH);
    }

    @Test
    void synchronizedCounterIsExact() throws InterruptedException {
        SynchronizedCounter counter = new SynchronizedCounter();

        Hammer.hammer(counter, THREADS, INCREMENTS_EACH);

        assertThat(counter.value()).isEqualTo(THREADS * INCREMENTS_EACH);
    }

    @Test
    void atomicCounterIsExact() throws InterruptedException {
        AtomicCounter counter = new AtomicCounter();

        Hammer.hammer(counter, THREADS, INCREMENTS_EACH);

        assertThat(counter.value()).isEqualTo(THREADS * INCREMENTS_EACH);
    }

    @Test
    void concurrentHashMapMergeCountsFromManyThreads() throws InterruptedException {
        // merge is atomic per key on a ConcurrentHashMap. On a plain HashMap the same code loses
        // counts and can even corrupt the map's internal structure.
        Map<String, Integer> wordCounts = new ConcurrentHashMap<>();
        List<String> words = List.of("dune", "emma", "dune", "cosmos", "dune");

        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            threads.add(Thread.ofPlatform().start(() -> {
                for (int round = 0; round < 1_000; round++) {
                    words.forEach(w -> wordCounts.merge(w, 1, Integer::sum));
                }
            }));
        }
        for (Thread t : threads) {
            t.join();
        }

        assertThat(wordCounts)
                .containsEntry("dune", 3 * 1_000 * THREADS)
                .containsEntry("emma", 1_000 * THREADS)
                .containsEntry("cosmos", 1_000 * THREADS);
    }

    @Test
    void interruptWakesASleepingThread() throws InterruptedException {
        AtomicBoolean interrupted = new AtomicBoolean();
        CountDownLatch started = new CountDownLatch(1);

        Thread sleeper = Thread.ofPlatform().start(() -> {
            started.countDown();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(1));
            } catch (InterruptedException e) {
                // Interruption is a polite request to stop, not a kill. The thread decides.
                interrupted.set(true);
            }
        });

        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        sleeper.interrupt();
        sleeper.join(5_000);

        assertThat(interrupted.get()).isTrue();
        assertThat(sleeper.isAlive()).isFalse();
    }
}
