package dev.playground.library.info;

/** Body of {@code GET /api/info/greetings}. Guide: §5.1 Spring Boot fundamentals. */
public record GreetingsResponse(String primary, String casual) {}
