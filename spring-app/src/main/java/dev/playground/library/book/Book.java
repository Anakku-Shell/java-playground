package dev.playground.library.book;

/**
 * A book as the application stores it. A plain mutable class until §5.4 makes it a JPA entity; it
 * never leaves the service layer. Guide: §5.2 REST API.
 */
public class Book {

    private Long id;
    private String isbn;
    private String title;
    private Integer publishedYear;
    private int totalCopies;

    public Book() {}

    public Book(String isbn, String title, Integer publishedYear, int totalCopies) {
        this.isbn = isbn;
        this.title = title;
        this.publishedYear = publishedYear;
        this.totalCopies = totalCopies;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
