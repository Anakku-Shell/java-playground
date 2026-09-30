package dev.playground.core.concurrency;

/**
 * Fix 1: a lock. {@code synchronized} lets one thread at a time into these methods (they share the
 * lock on {@code this}) and makes the write visible to the next thread that takes the lock. Both
 * methods need it: an unsynchronized {@code value()} could read a stale count. Guide: §4.3
 * Concurrency.
 */
public final class SynchronizedCounter implements HitCounter {

    private int count;

    @Override
    public synchronized void increment() {
        count++;
    }

    @Override
    public synchronized int value() {
        return count;
    }
}
