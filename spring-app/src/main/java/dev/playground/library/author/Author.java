package dev.playground.library.author;

/**
 * An author as the application stores it. A plain mutable class for now; §5.4 turns it into a JPA
 * entity, which needs exactly this shape (a no-argument constructor, getters and setters). It never
 * leaves the service layer: the API speaks {@code AuthorResponse}. Guide: §5.2 REST API.
 */
public class Author {

    private Long id;
    private String name;
    private Integer birthYear;

    public Author() {}

    public Author(String name, Integer birthYear) {
        this.name = name;
        this.birthYear = birthYear;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
}
