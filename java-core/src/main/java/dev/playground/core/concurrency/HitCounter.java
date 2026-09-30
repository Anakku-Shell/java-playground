package dev.playground.core.concurrency;

/** A counter that several threads increment at once. Guide: §4.3 Concurrency. */
public interface HitCounter {

    void increment();

    int value();
}
