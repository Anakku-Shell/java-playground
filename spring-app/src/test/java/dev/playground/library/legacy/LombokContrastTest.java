package dev.playground.library.legacy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.playground.library.config.LibraryProperties;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * LEGACY EXAMPLE: what Lombok generates, and where it differs from the records this project uses. Lombok
 * is an annotation processor: javac runs it while compiling, and it writes the methods straight into the
 * class file, so they exist although the source does not show them. Guide: §6 Legacy.
 */
@ExtendWith(OutputCaptureExtension.class)
class LombokContrastTest {

    /** The record this project would write instead of {@link LegacyBookDto}. */
    record BookDto(String isbn, String title, Integer publishedYear) {}

    @Test
    void dataAndBuilderGenerateTheJavaBeanBoilerplate() {
        LegacyBookDto dto = LegacyBookDto.builder()
                .isbn("9780441013593")
                .title("Dune")
                .publishedYear(1965)
                .build();

        dto.setTitle("Dune (40th anniversary)"); // @Data: a setter for every field

        assertThat(dto.getTitle()).isEqualTo("Dune (40th anniversary)");
        assertThat(dto)
                .hasToString("LegacyBookDto(isbn=9780441013593, title=Dune (40th anniversary), publishedYear=1965)");
    }

    @Test
    void dataEqualityFollowsTheFieldsSoAHashSetLosesAnObjectThatChanges() {
        LegacyBookDto dto = new LegacyBookDto("9780441013593", "Dune", 1965);
        Set<LegacyBookDto> set = new HashSet<>(Set.of(dto));

        dto.setTitle("Dune Messiah"); // the hash code changes with the field...

        // ...so the set looks in the wrong bucket. On a JPA entity this is the bug of §5.4.
        assertThat(set.contains(dto)).isFalse();
        assertThat(set).hasSize(1);
    }

    @Test
    void aRecordHasTheSameEqualityButCannotChange() {
        var dto = new BookDto("9780441013593", "Dune", 1965);
        Set<BookDto> set = new HashSet<>(Set.of(dto));

        // No setters: a "change" is a new object, and the one in the set stays findable.
        var renamed = new BookDto(dto.isbn(), "Dune Messiah", dto.publishedYear());

        assertThat(set).contains(dto).doesNotContain(renamed);
        assertThat(new BookDto("9780441013593", "Dune", 1965)).isEqualTo(dto);
    }

    @Test
    void valueIsTheImmutableClassARecordReplacesAndCanHideAField() {
        var member = new LegacyMember("ada@library.test", "secret");

        assertThat(member).isEqualTo(new LegacyMember("ada@library.test", "secret"));
        assertThat(member.getEmail()).isEqualTo("ada@library.test"); // getX(), where a record has x()
        assertThat(member).hasToString("LegacyMember(email=ada@library.test)"); // no password
    }

    @Test
    void requiredArgsConstructorIsConstructorInjectionAndSlf4jTheLogger(CapturedOutput output) {
        var properties = new LibraryProperties("Playground Library", new LibraryProperties.Loans(3, 14));

        var service = new LegacyGreetingService(properties); // the constructor Lombok wrote

        assertThat(service.greet("Ada")).isEqualTo("Welcome to Playground Library, Ada");
        assertThat(output).contains("Greeting Ada");
    }
}
