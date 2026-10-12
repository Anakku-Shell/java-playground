package dev.playground.library.common;

/** A request that breaks a business rule about current state (a duplicate ISBN...): 409. Guide: §5.3. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
