package dev.playground.library.config;

import java.util.Arrays;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Switches on {@code @Async}: the annotated method runs on a thread from this executor, and the
 * caller carries on at once. Like {@code @Transactional}, it works through a proxy, so a call from
 * inside the same class runs synchronously (§5.6).
 *
 * <p>One virtual thread per task (§4.3): cheap to create, so no pool to size. {@code AsyncConfigurer}
 * sets it for {@code @Async} only. The global switch, {@code spring.threads.virtual.enabled=true},
 * would also move Tomcat's request threads and the scheduler onto virtual threads.
 * Guide: §5.9 Beyond CRUD.
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Override
    public Executor getAsyncExecutor() {
        // Threads named async-1, async-2...: easy to spot in the log.
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("async-");
        executor.setVirtualThreads(true);
        return executor;
    }

    /**
     * A {@code void} {@code @Async} method has no caller left to throw to: without this handler its
     * exception would only reach a default log line. (A method returning a {@code Future} hands the
     * exception to whoever reads the future instead.)
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) ->
                log.error("@Async {} failed with arguments {}", method.getName(), Arrays.toString(params), ex);
    }
}
