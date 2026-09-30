package dev.playground.library.author;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A JPA entity mapped to the {@code authors} table. What JPA asks of an entity class:
 * <ul>
 *   <li>a no-argument constructor, at least protected: Hibernate instantiates it by reflection;
 *   <li>not {@code final}, and no {@code final} methods: lazy loading (§5.5) uses generated
 *       subclasses (proxies);
 *   <li>an {@code @Id}. Here the database generates it ({@code bigserial}), so there is no setter.
 * </ul>
 * It never leaves the service layer: the API speaks {@code AuthorResponse}. Guide: §5.4
 * Persistence with JPA.
 */
@Entity
@Table(name = "authors")
public class Author {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    private Integer birthYear;

    protected Author() {}

    public Author(String name, Integer birthYear) {
        this.name = name;
        this.birthYear = birthYear;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    /**
     * An entity is a database row, so two objects are the same author when they have the same id.
     * Not every field: the fields change while the row stays the same one. A new entity (no id yet)
     * is equal only to itself. {@code instanceof} and {@code getId()} (not {@code getClass()} and
     * the field) so that a Hibernate proxy, a generated subclass, still compares equal.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Author other && id != null && id.equals(other.getId());
    }

    /**
     * A constant: the id changes from null to a value on save, and a hash code must not change while
     * the object sits in a {@code HashSet}. Every author lands in one bucket, which is fine for the
     * small collections an entity ends up in.
     */
    @Override
    public int hashCode() {
        return Author.class.hashCode();
    }
}
