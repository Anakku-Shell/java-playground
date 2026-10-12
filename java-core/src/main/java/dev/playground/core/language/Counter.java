package dev.playground.core.language;

/**
 * A plain class: private state, overloaded constructors chained with {@code this(...)}, overloaded
 * methods and a static field shared by every instance. Guide: §4.1 Modern language.
 */
public class Counter {

    // static = one copy for the whole class. Not thread-safe; see chapter 03 for why.
    private static int created = 0;

    private final String name; // final = assigned exactly once, in the constructor
    private int value;

    public Counter(String name) {
        this(name, 0); // delegates to the other constructor (see Rect for code before this/super)
    }

    public Counter(String name, int start) {
        this.name = name;
        this.value = start;
        created++;
    }

    public void increment() {
        value++;
    }

    public void increment(int by) {
        value += by;
    }

    public int value() {
        return value;
    }

    public String name() {
        return name;
    }

    public static int created() {
        return created;
    }
}
