package io.github.madebyzwen.miniserver;

/**
 * Describes whether startup created a new local server or found the active one.
 */
public final class StartupResult implements AutoCloseable {

    public enum Kind {
        NEW_INSTANCE,
        EXISTING_INSTANCE
    }

    private final Kind kind;
    private final int port;
    private final RunningMiniServer runningServer;

    private StartupResult(Kind kind, int port, RunningMiniServer runningServer) {
        this.kind = kind;
        this.port = port;
        this.runningServer = runningServer;
    }

    static StartupResult newInstance(RunningMiniServer runningServer) {
        return new StartupResult(Kind.NEW_INSTANCE, runningServer.getPort(), runningServer);
    }

    static StartupResult existingInstance(int port) {
        return new StartupResult(Kind.EXISTING_INSTANCE, port, null);
    }

    public Kind getKind() {
        return kind;
    }

    public boolean isNewInstance() {
        return kind == Kind.NEW_INSTANCE;
    }

    public boolean isExistingInstance() {
        return kind == Kind.EXISTING_INSTANCE;
    }

    public int getPort() {
        return port;
    }

    public RunningMiniServer getRunningServer() {
        if (runningServer == null) {
            throw new IllegalStateException("An existing-instance result does not own the running server.");
        }
        return runningServer;
    }

    @Override
    public void close() {
        if (runningServer != null) {
            runningServer.close();
        }
    }
}
