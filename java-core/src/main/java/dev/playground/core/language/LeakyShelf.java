package dev.playground.core.language;

import java.util.List;

/**
 * A record that stores the caller's list as-is: its "immutability" stops at the reference.
 * Guide: §4.1 Modern language.
 */
public record LeakyShelf(String label, List<String> titles) {}
