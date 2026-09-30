package dev.playground.library.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * A library member, mapped to {@code members} (V2). The table has {@code created_at} only, so
 * instead of extending {@code AuditedEntity} the entity registers the auditing listener itself.
 * Same identity rules as {@code Author}. Guide: §5.5 Advanced JPA.
 */
@Entity
@Table(name = "members")
@EntityListeners(AuditingEntityListener.class)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Always lowercase (MemberMapper), so the unique constraint compares emails case-insensitively.
    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 200)
    private String fullName;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Optimistic locking (§5.6): no endpoint edits a member, but every borrow bumps the version
    // (MemberRepository.findWithVersionIncrementById), so two borrows by one member collide.
    @Version
    private long version;

    protected Member() {}

    public Member(String email, String fullName) {
        this.email = email;
        this.fullName = fullName;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Member other && id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Member.class.hashCode();
    }
}
