package dev.playground.library.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.Async;

/**
 * The {@code @Async} executor of {@link AsyncConfig}, in a context with nothing else: tasks run on
 * virtual threads named {@code async-}, and closing the context waits for the ones still running,
 * so a shutdown does not cut a notification in half. Guide: §5.9 Beyond CRUD.
 */
class AsyncConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(AsyncConfig.class, SlowTask.class);

    /**
     * An {@code @Async} bean whose task takes a while. The test reads its state through methods: the
     * bean in the context is a proxy (a subclass), and a proxy's own fields are never set.
     */
    static class SlowTask {

        private final CountDownLatch started = new CountDownLatch(1);
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicReference<Thread> thread = new AtomicReference<>();

        @Async
        public void run() throws InterruptedException {
            thread.set(Thread.currentThread());
            started.countDown();
            Thread.sleep(500);
            finished.set(true);
        }

        public boolean awaitStart() throws InterruptedException {
            return started.await(5, TimeUnit.SECONDS);
        }

        public boolean isFinished() {
            return finished.get();
        }

        public Thread thread() {
            return thread.get();
        }
    }

    @Test
    void tasksRunOnNamedVirtualThreads() {
        runner.run(context -> {
            SlowTask task = context.getBean(SlowTask.class);
            task.run();

            assertThat(task.awaitStart()).isTrue();
            assertThat(task.thread().isVirtual()).isTrue();
            assertThat(task.thread().getName()).startsWith("async-");
        });
    }

    @Test
    void closingTheContextWaitsForRunningTasks() {
        AtomicReference<SlowTask> task = new AtomicReference<>();

        runner.run(context -> {
            task.set(context.getBean(SlowTask.class));
            task.get().run();
            assertThat(task.get().awaitStart()).isTrue();
        }); // the context closes here, about half a second before the task ends

        assertThat(task.get().isFinished()).isTrue();
    }
}
