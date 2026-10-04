package dev.playground.library.config;

import java.util.Arrays;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Switches on {@code @Async}: the annotated method runs on a thread from this executor, and the
 * caller carries on at once. Like {@code @Transactional}, it works through a proxy, so a call from
 * inside the same class runs synchronously (§5.6).
 *
 * <p>One virtual thread per task (§4.3): cheap to create, so no pool to size. The
 * {@code AsyncConfigurer} bean sets it for {@code @Async} only. The global switch,
 * {@code spring.threads.virtual.enabled=true}, would also move Tomcat's request threads and the
 * scheduler onto virtual threads. Guide: §5.9 Beyond CRUD.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    /**
     * A bean, not an object created inside the configurer: Spring then closes it when the application
     * stops ({@code close()}, since it is {@code AutoCloseable}), and with a termination timeout
     * {@code close()} waits for the tasks still running instead of leaving them to be cut off. After 10
     * seconds the ones still running are interrupted; from the first moment of the close, new tasks are
     * rejected.
     *
     * <p>It is not a {@code SmartLifecycle} (a {@code ThreadPoolTaskExecutor} is): the wait happens while
     * beans are destroyed, after the web server and the Kafka listeners have stopped. A task that needs
     * another bean (the database, a mail sender) relies on that bean being destroyed later.
     *
     * <p>Being an {@code Executor} bean, it also makes Boot skip its own {@code applicationTaskExecutor}
     * (unless {@code spring.task.execution.mode=force}), which this application does not use (no async
     * MVC requests).
     */
    @Bean
    SimpleAsyncTaskExecutor asyncExecutor() {
        // Threads named async-1, async-2...: easy to spot in the log.
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("async-");
        executor.setVirtualThreads(true);
        executor.setTaskTerminationTimeout(10_000);
        return executor;
    }

    @Bean
    AsyncConfigurer asyncConfigurer(SimpleAsyncTaskExecutor asyncExecutor) {
        return new AsyncConfigurer() {
            @Override
            public Executor getAsyncExecutor() {
                return asyncExecutor;
            }

            /**
             * A {@code void} {@code @Async} method has no caller left to throw to: without this handler
             * its exception would only reach a default log line. (A method returning a {@code Future}
             * hands the exception to whoever reads the future instead.)
             */
            @Override
            public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
                return (ex, method, params) ->
                        log.error("@Async {} failed with arguments {}", method.getName(), Arrays.toString(params), ex);
            }
        };
    }
}
