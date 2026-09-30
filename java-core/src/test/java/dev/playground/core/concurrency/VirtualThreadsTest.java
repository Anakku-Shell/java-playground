package dev.playground.core.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Virtual threads (Java 21): threads managed by the JVM, not the OS. They are cheap enough to
 * create one per task, even millions of them. Guide: §4.3 Concurrency.
 *
 * <p>When a virtual thread blocks (sleep, socket read, JDBC call), the JVM unmounts it and its
 * carrier (a platform thread) runs another virtual thread meanwhile. So they help with <b>waiting</b>:
 * many concurrent blocking calls. They do not make CPU-bound work faster: by default there are only
 * as many carriers as CPU cores.
 *
 * <p>A virtual thread that cannot be unmounted "pins" its carrier while it blocks: during a class
 * initialiser, or with native (JNI/FFM) code on its stack. Since Java 24 (JEP 491) {@code
 * synchronized} no longer pins. File I/O does not unmount either; the scheduler adds a temporary
 * carrier to make up for it.
 */
@Timeout(30)
class VirtualThreadsTest {

    @Test
    void threadOfVirtualStartsAVirtualThread() throws InterruptedException {
        AtomicReference<Thread> seen = new AtomicReference<>();

        Thread virtual = Thread.ofVirtual().name("reader-", 1).start(() -> seen.set(Thread.currentThread()));
        virtual.join();

        assertThat(seen.get().isVirtual()).isTrue();
        assertThat(seen.get().getName()).isEqualTo("reader-1");
        // Virtual threads are always daemon threads: they never keep the JVM alive on their own.
        assertThat(seen.get().isDaemon()).isTrue();
        assertThat(Thread.currentThread().isVirtual()).isFalse(); // JUnit runs on a platform thread
    }

    @Test
    void tenThousandSleepingTasksFinishQuickly() {
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();

        // One new virtual thread per task, no pool size to tune.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                executor.submit(() -> {
                    Thread.sleep(10); // stands in for a blocking call: HTTP, JDBC, a socket read
                    done.incrementAndGet();
                    return null;
                });
            }
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(done.get()).isEqualTo(10_000);
        // All 10 000 sleeps overlap. Usually well under a second; the bound is loose for slow CI.
        assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void aFixedPlatformPoolQueuesTheWaiting() {
        // A pool of 10 platform threads can only have 10 sleeps in flight: 100 tasks of 10 ms take
        // at least 100 / 10 × 10 ms = 100 ms (sleep never returns early). The same 10 000 tasks as
        // above would take 10 s. A bigger pool helps, but each platform thread is an OS thread
        // with its own stack, so you get thousands of them, not millions.
        long start = System.nanoTime();

        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            for (int i = 0; i < 100; i++) {
                pool.submit(() -> {
                    Thread.sleep(10);
                    return null;
                });
            }
        }
        Duration elapsed = Duration.ofNanos(System.nanoTime() - start);

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(100));
    }
}
