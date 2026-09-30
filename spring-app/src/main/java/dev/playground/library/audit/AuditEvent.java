package dev.playground.library.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * One line of the audit trail, mapped to {@code audit_events} (V3). Written once and never
 * changed, so no setters and no version. Guide: §5.6 Transactions.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 500)
    private String detail;

    @Column(nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {}

    public AuditEvent(String action, String detail, Instant occurredAt) {
        this.action = action;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getAction() {
        return action;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof AuditEvent other && id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return AuditEvent.class.hashCode();
    }
}
