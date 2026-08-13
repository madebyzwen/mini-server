package io.github.madebyzwen.miniserver;

/**
 * Reports invalid persistence data or a persistence filesystem failure.
 */
final class PersistenceException extends Exception {

    private static final long serialVersionUID = 1L;

    enum Reason {
        INVALID_DATA,
        IO_FAILURE,
        WRITE_LOCK_TIMEOUT
    }

    private final Reason reason;

    PersistenceException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    PersistenceException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    Reason getReason() {
        return reason;
    }
}
