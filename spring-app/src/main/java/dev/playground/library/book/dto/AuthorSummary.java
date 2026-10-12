package dev.playground.library.book.dto;

/** The short form of an author inside a {@link BookResponse}. Guide: §5.2 REST API. */
public record AuthorSummary(Long id, String name) {}
