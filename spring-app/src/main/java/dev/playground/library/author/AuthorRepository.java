package dev.playground.library.author;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Everything the author use cases need ({@code findAll}, {@code findById}, {@code save},
 * {@code existsById}, {@code deleteById}) comes from {@code JpaRepository}; Spring Data generates
 * the implementation. See {@code BookRepository} for custom queries. Guide: §5.4 Persistence with
 * JPA.
 */
public interface AuthorRepository extends JpaRepository<Author, Long> {}
