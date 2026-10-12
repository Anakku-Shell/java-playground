package dev.playground.library.legacy;

import static dev.playground.library.testing.TestDataFactory.dune;
import static dev.playground.library.testing.TestDataFactory.duneMessiah;
import static dev.playground.library.testing.TestDataFactory.herbert;
import static dev.playground.library.testing.TestDataFactory.leGuin;
import static dev.playground.library.testing.TestDataFactory.withId;
import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.book.Book;
import dev.playground.library.book.BookMapper;
import dev.playground.library.book.dto.UpdateBookRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

/**
 * LEGACY EXAMPLE: the MapStruct mapper {@link BookLegacyMapper} against the hand-written {@link BookMapper}
 * the application uses. Both must give the same result; the difference is who writes the code. MapStruct
 * generates {@code BookLegacyMapperImpl} at compile time (see target/generated-test-sources). Guide: §6 Legacy.
 */
class MapStructContrastTest {

    // Default component model: a plain object. With componentModel = "spring" the Impl is a bean you inject.
    private final BookLegacyMapper mapper = Mappers.getMapper(BookLegacyMapper.class);

    @Test
    void theGeneratedMapperBuildsTheSameResponseAsTheManualOne() {
        Book book = withId(dune(), 7);
        book.replaceAuthors(Set.of(withId(leGuin(), 2), withId(herbert(), 1))); // sorted by name in the response

        assertThat(mapper.toResponse(book, 1)).isEqualTo(BookMapper.toResponse(book, 1));
        assertThat(mapper.toResponse(book, 1).availableCopies()).isEqualTo(2);
    }

    @Test
    void theGeneratedMapperUpdatesAnEntityLikeTheManualOne() {
        var request = new UpdateBookRequest("0-441-01359-7", "Dune (revised)", 1966, 5, Set.of());
        Book manual = withId(duneMessiah(), 7); // another ISBN, so the new one must be written
        Book generated = withId(duneMessiah(), 7);

        BookMapper.apply(request, manual);
        mapper.apply(request, generated);

        assertThat(generated)
                .usingRecursiveComparison()
                .comparingOnlyFields("isbn", "title", "publishedYear", "totalCopies")
                .isEqualTo(manual);
        assertThat(generated.getIsbn()).isEqualTo("9780441013593"); // the ISBN-10 stored as ISBN-13
    }
}
