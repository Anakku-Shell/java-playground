package dev.playground.library.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Same identity rules as {@code Author}. The credentials (V4) are how a member logs in. Guide:
 * §5.5 Advanced JPA, §5.7 Security.
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

    // A PasswordEncoder hash ("{bcrypt}$2a$10$..."), or null: a member without one cannot log in.
    // Never returned by the API: MemberResponse has no such field.
    @Column(length = 255)
    private String passwordHash;

    // STRING stores the name ("LIBRARIAN"). The default, ORDINAL, stores the position (1), which
    // silently changes meaning if someone reorders or inserts a constant.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    // Optimistic locking (§5.6): no endpoint edits a member, but every borrow bumps the version
    // (MemberRepository.findWithVersionIncrementById), so two borrows by one member collide.
    @Version
    private long version;

    protected Member() {}

    public Member(String email, String fullName, String passwordHash, Role role) {
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
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

    public String getPasswordHash() {
        return passwordHash;
    }

    public Role getRole() {
        return role;
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
