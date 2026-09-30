package dev.playground.library.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Creation and last-change timestamps for the entities that extend it ({@code Author},
 * {@code Book}). A {@code @MappedSuperclass} is not an entity and has no table: its fields become
 * columns of each subclass's table.
 *
 * <p>{@code AuditingEntityListener} is a JPA entity listener: Hibernate calls it on {@code persist}
 * ({@code @PrePersist}) and before it flushes an UPDATE of the row ({@code @PreUpdate}), and it fills
 * the annotated fields (enabled by {@code JpaAuditingConfig}). A change to a collection alone (the
 * book_authors rows) updates the row only because {@code Book} has a {@code @Version}: Hibernate
 * bumps it, and {@code updatedAt} moves with it. An unversioned entity would keep its
 * {@code updatedAt}. No setters: nothing else writes them. Guide: §5.5 Advanced JPA, §5.6.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditedEntity {

    @CreatedDate
    @Column(nullable = false, updatable = false) // updatable = false: never part of an UPDATE
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
