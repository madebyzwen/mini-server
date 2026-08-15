package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the active HTTP server and its lifetime instance lock.
 */
public final class RunningMiniServer implements AutoCloseable {

    private final HttpServer httpServer;
    private final FileLock instanceLock;
    private final FileChannel instanceLockChannel;
    private final RuntimeStateStore stateStore;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CountDownLatch termination = new CountDownLatch(1);

    RunningMiniServer(
            HttpServer httpServer,
            FileLock instanceLock,
            FileChannel instanceLockChannel,
            RuntimeStateStore stateStore) {
        this.httpServer = httpServer;
        this.instanceLock = instanceLock;
        this.instanceLockChannel = instanceLockChannel;
        this.stateStore = stateStore;
    }

    public int getPort() {
        return getAddress().getPort();
    }

    public InetSocketAddress getAddress() {
        return httpServer.getAddress();
    }

    public boolean isRunning() {
        return !closed.get();
    }

    public void awaitTermination() throws InterruptedException {
        termination.await();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }

        try {
            httpServer.stop(0);
        } finally {
            try {
                stateStore.invalidate();
            } catch (IOException | SecurityException ignored) {
                // The lifetime lock still determines that the server has stopped.
            }
            try {
                instanceLock.release();
            } catch (IOException ignored) {
                // Closing the channel below also releases any lock it owns.
            }
            try {
                instanceLockChannel.close();
            } catch (IOException ignored) {
                // The server is already stopped and no further recovery is possible here.
            }
            termination.countDown();
        }
    }
}
