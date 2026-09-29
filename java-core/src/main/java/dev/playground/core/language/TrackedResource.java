package dev.playground.core.language;

import java.util.List;

/**
 * An {@link AutoCloseable} that records when it opens and closes, to show what try-with-resources
 * does. Guide: §4.1 Modern language.
 */
public class TrackedResource implements AutoCloseable {

    private final String name;
    private final List<String> log;
    private final boolean failOnClose;

    public TrackedResource(String name, List<String> log) {
        this(name, log, false);
    }

    public TrackedResource(String name, List<String> log, boolean failOnClose) {
        this.name = name;
        this.log = log;
        this.failOnClose = failOnClose;
        log.add("open " + name);
    }

    // Narrower than AutoCloseable.close() (which throws Exception): callers need no catch.
    @Override
    public void close() {
        log.add("close " + name);
        if (failOnClose) {
            throw new IllegalStateException("close " + name + " failed");
        }
    }
}
