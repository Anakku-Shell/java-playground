package dev.playground.library.legacy;

import dev.playground.library.author.Author;
import dev.playground.library.book.Book;
import dev.playground.library.book.dto.AuthorSummary;
import dev.playground.library.book.dto.BookResponse;
import dev.playground.library.book.dto.UpdateBookRequest;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

/**
 * LEGACY EXAMPLE: {@code BookMapper} written with MapStruct. You declare the methods; the annotation
 * processor writes {@code BookLegacyMapperImpl} at compile time, matching properties by name (getters and
 * setters, or a record's constructor). What names cannot say goes into {@code @Mapping}: a Java
 * {@code expression}, a {@code source} path, or {@code ignore}. {@code unmappedTargetPolicy = ERROR} turns a
 * target property nobody maps into a compile error instead of a silent null. Guide: §6 Legacy.
 */
@Mapper(unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface BookLegacyMapper {

    // Everything but availableCopies matches by name: only Book has these properties, so MapStruct finds
    // them without a "book." path. authors (Set<Author> to List<AuthorSummary>) goes through sortedSummaries
    // below: MapStruct picks any method of the mapper whose types fit.
    @Mapping(target = "availableCopies", expression = "java((int) (book.getTotalCopies() - activeLoans))")
    BookResponse toResponse(Book book, long activeLoans);

    // Book has no setter for id, version or the audit dates, so they are not targets. Authors (a getter-only
    // collection) is: the service sets it after looking the ids up.
    @Mapping(target = "isbn", expression = "java(dev.playground.library.book.Isbn.toIsbn13(request.isbn()))")
    @Mapping(target = "authors", ignore = true)
    void apply(UpdateBookRequest request, @MappingTarget Book book);

    /** Hand-written help: a default method is used as is. */
    default List<AuthorSummary> sortedSummaries(Set<Author> authors) {
        return authors.stream()
                .sorted(Comparator.comparing(Author::getName).thenComparing(Author::getId))
                .map(author -> new AuthorSummary(author.getId(), author.getName()))
                .toList();
    }
}
