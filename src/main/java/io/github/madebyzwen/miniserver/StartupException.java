package io.github.madebyzwen.miniserver;

/**
 * Reports a failure to establish or discover the local Mini Server instance.
 */
public final class StartupException extends Exception {

    private static final long serialVersionUID = 1L;

    public StartupException(String message) {
        super(message);
    }

    public StartupException(String message, Throwable cause) {
        super(message, cause);
    }
}
