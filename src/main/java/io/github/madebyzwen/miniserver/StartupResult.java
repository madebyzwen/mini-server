package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.net.InetSocketAddress;

public final class StartupResult implements AutoCloseable {
    private final boolean newInstance;
    private final int port;
    private final RunningMiniServer runningServer;

    private StartupResult(boolean newInstance, int port, RunningMiniServer runningServer) {
        this.newInstance = newInstance;
        this.port = port;
        this.runningServer = runningServer;
    }

    static StartupResult started(RunningMiniServer runningServer) {
        return new StartupResult(true, runningServer.getAddress().getPort(), runningServer);
    }

    static StartupResult existing(int port) {
        return new StartupResult(false, port, null);
    }

    public boolean isNewInstance() {
        return newInstance;
    }

    public int getPort() {
        return port;
    }

    public InetSocketAddress getAddress() {
        if (runningServer != null) {
            return runningServer.getAddress();
        }
        return new InetSocketAddress(MiniServerStartup.LOOPBACK_ADDRESS, port);
    }

    public void awaitTermination() throws InterruptedException {
        if (runningServer != null) {
            runningServer.awaitTermination();
        }
    }

    @Override
    public void close() throws IOException {
        if (runningServer != null) {
            runningServer.close();
        }
    }
}
