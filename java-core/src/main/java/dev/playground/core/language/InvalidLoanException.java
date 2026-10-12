package dev.playground.core.language;

/**
 * A custom <b>unchecked</b> exception (extends RuntimeException): no {@code throws} needed.
 * Use for programming errors and broken rules. Spring code relies almost only on these.
 * Guide: §4.1 Modern language.
 */
public class InvalidLoanException extends RuntimeException {

    public InvalidLoanException(String message) {
        super(message);
    }
}
