package dev.playground.core.concurrency;

/**
 * Not thread-safe: {@code count++} is three steps (read, add, write), and two threads can
 * interleave them and lose an update. Guide: §4.3 Concurrency.
 */
public final class UnsafeCounter implements HitCounter {

    private int count;

    @Override
    public void increment() {
        count++;
    }

    @Override
    public int value() {
        return count;
    }
}
