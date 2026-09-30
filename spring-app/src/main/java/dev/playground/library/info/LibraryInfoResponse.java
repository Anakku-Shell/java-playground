package dev.playground.library.info;

import java.util.List;

/**
 * Body of {@code GET /api/info}. DTOs are records: Jackson writes each component as a JSON field.
 * Guide: §5.1 Spring Boot fundamentals.
 */
public record LibraryInfoResponse(
        String application, String name, int maxActiveLoans, int loanDurationDays, List<String> activeProfiles) {}
