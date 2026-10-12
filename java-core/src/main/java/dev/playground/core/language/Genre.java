package dev.playground.core.language;

/**
 * An enum with a field, a constructor and methods. Guide: §4.1 Modern language.
 */
public enum Genre {
    FICTION("Fiction", true),
    SCIENCE("Science", false),
    HISTORY("History", false),
    FANTASY("Fantasy", true);

    private final String label;
    private final boolean fiction;

    // Enum constructors are implicitly private: only the constants above can call them.
    Genre(String label, boolean fiction) {
        this.label = label;
        this.fiction = fiction;
    }

    public String label() {
        return label;
    }

    public boolean isFiction() {
        return fiction;
    }

    /** Looks a genre up by its label; valueOf only knows the constant names. */
    public static Genre fromLabel(String label) {
        for (Genre genre : values()) {
            if (genre.label.equals(label)) {
                return genre;
            }
        }
        throw new IllegalArgumentException("Unknown genre: " + label);
    }
}
