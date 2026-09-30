package dev.playground.library.author;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Same identity rules as {@code BookTest}. Guide: §5.4 Persistence with JPA. */
class AuthorTest {

    private static Author withId(Long id) {
        Author author = new Author("Frank Herbert", 1920);
        ReflectionTestUtils.setField(author, "id", id);
        return author;
    }

    @Test
    void equalityFollowsTheId() {
        assertThat(withId(1L)).isEqualTo(withId(1L)).isNotEqualTo(withId(2L));
        assertThat(new Author("Frank Herbert", 1920)).isNotEqualTo(new Author("Frank Herbert", 1920));
    }

    @Test
    void staysInAHashSetWhenTheIdIsAssigned() {
        Author author = new Author("Frank Herbert", 1920);
        Set<Author> authors = new HashSet<>(Set.of(author));

        ReflectionTestUtils.setField(author, "id", 1L);

        assertThat(authors).contains(author);
    }
}
