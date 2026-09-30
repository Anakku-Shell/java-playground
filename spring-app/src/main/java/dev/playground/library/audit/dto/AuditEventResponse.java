package dev.playground.library.audit.dto;

import java.time.Instant;

/** What the API returns for an audit event. Guide: §5.6 Transactions. */
public record AuditEventResponse(Long id, String action, String detail, Instant occurredAt) {}
