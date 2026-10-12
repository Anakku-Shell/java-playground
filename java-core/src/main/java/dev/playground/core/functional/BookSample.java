package dev.playground.core.functional;

import dev.playground.core.language.Genre;

/** A book for the stream and Optional examples. Guide: §4.2 Collections &amp; functional. */
public record BookSample(String title, String author, int year, Genre genre, int pages) {}
