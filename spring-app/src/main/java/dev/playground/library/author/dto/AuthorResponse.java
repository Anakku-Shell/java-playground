package dev.playground.library.author.dto;

/** What the API returns for an author. Guide: §5.2 REST API. */
public record AuthorResponse(Long id, String name, Integer birthYear) {}
