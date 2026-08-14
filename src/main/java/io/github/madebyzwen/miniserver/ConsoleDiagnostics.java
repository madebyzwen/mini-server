package io.github.madebyzwen.miniserver;

import java.io.PrintStream;

/**
 * Emits concise transient diagnostics without introducing persistent logging.
 */
final class ConsoleDiagnostics {

    private final PrintStream errorOutput;

    ConsoleDiagnostics(PrintStream errorOutput) {
        if (errorOutput == null) {
            throw new NullPointerException("Diagnostic output must not be null.");
        }
        this.errorOutput = errorOutput;
    }

    void report(String subsystem, Throwable failure) {
        errorOutput.println("Mini Server " + subsystem + " failed: " + failureSummary(failure));
    }

    static String failureSummary(Throwable failure) {
        String summary = exceptionSummary(failure, true);
        Throwable cause = relevantCause(failure);
        if (cause == null) {
            return summary;
        }

        boolean includeCauseMessage = !(failure instanceof PersistenceException)
                || ((PersistenceException) failure).getReason()
                != PersistenceException.Reason.INVALID_DATA;
        String causeSummary = exceptionSummary(cause, includeCauseMessage);
        return summary.contains(causeSummary)
                ? summary
                : summary + "; caused by " + causeSummary;
    }

    private static Throwable relevantCause(Throwable failure) {
        if (failure instanceof PersistenceException || failure instanceof StartupException) {
            Throwable cause = failure.getCause();
            return cause == failure ? null : cause;
        }
        return null;
    }

    private static String exceptionSummary(Throwable failure, boolean includeMessage) {
        if (failure == null) {
            return "Unknown failure";
        }

        String type = failure.getClass().getSimpleName();
        if (type.isEmpty()) {
            type = failure.getClass().getName();
        }
        if (!includeMessage || failure.getMessage() == null) {
            return type;
        }

        String message = failure.getMessage().replace('\r', ' ').replace('\n', ' ').trim();
        return message.isEmpty() ? type : type + ": " + message;
    }
}
