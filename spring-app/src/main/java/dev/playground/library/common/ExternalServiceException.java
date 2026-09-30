package dev.playground.library.common;

/**
 * A service this API depends on failed or timed out: 502 Bad Gateway. The cause stays in the log,
 * never in the response. First used by the Open Library client (§5.9). Guide: §5.3.
 */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String service, Throwable cause) {
        super(service + " is unavailable", cause);
    }
}
