package dev.playground.library.common;

/**
 * A resource that does not exist: "Book 7 not found". Unchecked, and free of HTTP types, so
 * services can throw it; {@link GlobalExceptionHandler} turns it into a 404. Guide: §5.3.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found");
    }
}
