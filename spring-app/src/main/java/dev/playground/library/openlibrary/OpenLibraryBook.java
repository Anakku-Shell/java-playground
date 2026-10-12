package dev.playground.library.openlibrary;

import java.util.List;

/**
 * A book as Open Library describes it, in this API's terms: what an import needs, and the body of
 * the import preview. Open Library's own JSON shape stays in {@link OpenLibraryApi}. Years are null
 * when Open Library has no date with a year in it. Guide: §5.9 Beyond CRUD.
 */
public record OpenLibraryBook(String isbn, String title, Integer publishedYear, List<Author> authors) {

    public record Author(String name, Integer birthYear) {}
}
