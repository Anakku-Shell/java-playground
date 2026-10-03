package dev.playground.core.legacy;

/** The modern twin of {@link LegacyOrder}: the same value class as a record. Guide: §6 Legacy. */
public record Order(String customer, String category, int amount) {}
