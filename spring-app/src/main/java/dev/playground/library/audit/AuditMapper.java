package dev.playground.library.audit;

import dev.playground.library.audit.dto.AuditEventResponse;

/** Manual mapping from {@link AuditEvent} to its DTO. Guide: §5.6 Transactions. */
public final class AuditMapper {

    private AuditMapper() {}

    public static AuditEventResponse toResponse(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getAction(), event.getDetail(), event.getOccurredAt());
    }
}
