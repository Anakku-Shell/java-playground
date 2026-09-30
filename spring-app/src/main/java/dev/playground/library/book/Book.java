package dev.playground.library.book;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A JPA entity: Hibernate maps it to the {@code books} table created by Flyway (V1). It never leaves
 * the service layer. See {@code Author} for the rules every entity here follows.
 * Guide: §5.4 Persistence with JPA.
 */
@Entity
@Table(name = "books")
public class Book {

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
