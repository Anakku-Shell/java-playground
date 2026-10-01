package dev.playground.library.testing;

import dev.playground.library.author.Author;
import dev.playground.library.book.Book;
import dev.playground.library.book.dto.CreateBookRequest;
import dev.playground.library.book.dto.UpdateBookRequest;
import dev.playground.library.member.Member;
import dev.playground.library.member.Role;
import java.util.Set;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * The test data the suite keeps reaching for, built in one place. Each method returns a new,
 * unsaved instance, so a test can change it freely. A test names only what it is about: the rest
 * comes from here, and a change to an entity's constructor is one edit, not twenty.
 * Guide: §5.8 Testing.
 */
public final class TestDataFactory {

    private TestDataFactory() {}

    /**
     * Sets the id the database would assign. Entities have no {@code setId} on purpose (Hibernate
     * owns it), so unit tests, which have no database, set the field by reflection.
     */
    public static <T> T withId(T entity, long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }

    public static Author herbert() {
        return new Author("Frank Herbert", 1920);
    }

    public static Author leGuin() {
        return new Author("Ursula K. Le Guin", 1929);
    }

    /** Three copies, no authors. */
    public static Book dune() {
        return new Book("9780441013593", "Dune", 1965, 3);
    }

    public static Book duneMessiah() {
        return new Book("9780593098233", "Dune Messiah", 1969, 2);
    }

    public static Book emma() {
        return new Book("9780141439587", "Emma", 1815, 2);
    }

    /** No password: enough for anything but logging in (tokens come from TokenService or jwt()). */
    public static Member ada() {
        return new Member("ada@library.test", "Ada Lovelace", null, Role.MEMBER);
    }

    public static Member alan() {
        return new Member("alan@library.test", "Alan Turing", null, Role.MEMBER);
    }

    public static Member librarian() {
        return new Member("librarian@library.test", "Libby Rarian", null, Role.LIBRARIAN);
    }

    /** A request for Dune with three copies and no authors; change only what the test is about. */
    public static BookRequestBuilder aBookRequest() {
        return new BookRequestBuilder();
    }

    /**
     * A test data builder: records have no "with" methods, and a five-argument constructor call hides
     * which argument a test cares about. {@code aBookRequest().totalCopies(1).update()} says it.
     */
    public static final class BookRequestBuilder {

        private String isbn = "9780441013593";
        private String title = "Dune";
        private Integer publishedYear = 1965;
        private int totalCopies = 3;
        private Set<Long> authorIds = Set.of();

        private BookRequestBuilder() {}

        public BookRequestBuilder isbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public BookRequestBuilder title(String title) {
            this.title = title;
            return this;
        }

        public BookRequestBuilder publishedYear(Integer publishedYear) {
            this.publishedYear = publishedYear;
            return this;
        }

        public BookRequestBuilder totalCopies(int totalCopies) {
            this.totalCopies = totalCopies;
            return this;
        }

        public BookRequestBuilder authorIds(Long... authorIds) {
            this.authorIds = Set.of(authorIds);
            return this;
        }

        /** No {@code authorIds} in the JSON at all: the API treats it as "no authors". */
        public BookRequestBuilder withoutAuthorIds() {
            this.authorIds = null;
            return this;
        }

        public CreateBookRequest create() {
            return new CreateBookRequest(isbn, title, publishedYear, totalCopies, authorIds);
        }

        public UpdateBookRequest update() {
            return new UpdateBookRequest(isbn, title, publishedYear, totalCopies, authorIds);
        }
    }
}
