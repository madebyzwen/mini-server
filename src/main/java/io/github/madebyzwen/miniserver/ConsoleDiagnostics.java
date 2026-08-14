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
        String type = failure == null ? "Unknown failure" : failure.getClass().getSimpleName();
        if (failure == null || failure.getMessage() == null) {
            return type;
        }

        String message = failure.getMessage().replace('\r', ' ').replace('\n', ' ').trim();
        return message.isEmpty() ? type : type + ": " + message;
    }
}
