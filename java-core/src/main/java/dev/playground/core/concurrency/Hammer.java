package dev.playground.core.concurrency;

import java.util.ArrayList;
import java.util.List;

/** Starts several threads that all increment the same counter. Guide: §4.3 Concurrency. */
public final class Hammer {

    private Hammer() {}

    public static void hammer(HitCounter counter, int threads, int incrementsEach) throws InterruptedException {
        List<Thread> started = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            started.add(Thread.ofPlatform().start(() -> {
                for (int n = 0; n < incrementsEach; n++) {
                    counter.increment();
                }
            }));
        }
        // join also guarantees that everything the thread wrote is visible here afterwards.
        for (Thread t : started) {
            t.join();
        }
    }
}
