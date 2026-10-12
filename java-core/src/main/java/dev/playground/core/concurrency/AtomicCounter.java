package dev.playground.core.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fix 2: an atomic variable. {@code incrementAndGet} does read-add-write as one atomic hardware
 * operation, with no lock (lock-free). {@code compareAndSet} is the general building block: "set it
 * to b only if it is still a". Best for a single shared number. Guide: §4.3 Concurrency.
 */
public final class AtomicCounter implements HitCounter {

    private final AtomicInteger count = new AtomicInteger();

    @Override
    public void increment() {
        count.incrementAndGet();
    }

    @Override
    public int value() {
        return count.get();
    }
}
