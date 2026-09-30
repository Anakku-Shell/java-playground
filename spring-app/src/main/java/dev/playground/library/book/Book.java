package dev.playground.library.book;

import dev.playground.library.author.Author;
import dev.playground.library.common.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.hibernate.annotations.BatchSize;

/**
 * A JPA entity: Hibernate maps it to the {@code books} table created by Flyway (V1). It never leaves
 * the service layer. See {@code Author} for the rules every entity here follows. The authors
 * relation and the audit timestamps arrive in §5.5. Guide: §5.4 Persistence with JPA, §5.5.
 */
@Entity
@Table(name = "books")
public class Book extends AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // With ddl-auto: validate, unique/length/nullable only document the column (they would drive
    // DDL generation). The migration is what creates the constraint.
    @Column(nullable = false, unique = true, length = 13)
    private String isbn;

    @Column(nullable = false, length = 300)
    private String title;

    // Column name from the naming strategy: publishedYear -> published_year.
    private Integer publishedYear;

    @Column(nullable = false)
    private int totalCopies;

    // The owning side of the relation: Hibernate writes book_authors from this set (Author.books is
    // the inverse side). A collection is LAZY by default: the set is loaded the first time it is
    // touched, inside a transaction. @BatchSize is the N+1 fix for lists (§5.5): touching one
    // book's authors loads those of up to 100 books in the persistence context in one query (100 is
    // the max page size), instead of one query per book.
    @ManyToMany
    @JoinTable(
            name = "book_authors",
            joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    @BatchSize(size = 100)
    private Set<Author> authors = new HashSet<>();

    protected Book() {}

    public Book(String isbn, String title, Integer publishedYear, int totalCopies) {
        this.isbn = isbn;
        this.title = title;
        this.publishedYear = publishedYear;
        this.totalCopies = totalCopies;
    }

    public Long getId() {
        return id;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Integer getPublishedYear() {
        return publishedYear;
    }

    public void setPublishedYear(Integer publishedYear) {
        this.publishedYear = publishedYear;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(int totalCopies) {
        this.totalCopies = totalCopies;
    }

    /** Read-only view: links change through {@link #replaceAuthors}. */
    public Set<Author> getAuthors() {
        return Collections.unmodifiableSet(authors);
    }

    /**
     * Makes {@code newAuthors} the book's authors. {@code retainAll} + {@code addAll} rather than
     * {@code clear} + {@code addAll}: the links that stay are never removed, so Hibernate sends a
     * DELETE only for the authors that left and an INSERT only for the new ones.
     */
    public void replaceAuthors(Set<Author> newAuthors) {
        authors.retainAll(newAuthors);
        authors.addAll(newAuthors);
    }

    /** Same row, same book; see {@code Author#equals} for why. */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Book other && id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return Book.class.hashCode();
    }
}
