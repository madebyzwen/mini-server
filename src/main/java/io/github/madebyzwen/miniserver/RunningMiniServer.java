package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.CountDownLatch;

final class RunningMiniServer {
    private final HttpServer httpServer;
    private final InetSocketAddress address;
    private final FileLock instanceLock;
    private final FileChannel lockChannel;
    private final CountDownLatch closedSignal = new CountDownLatch(1);

    private boolean closed;

    RunningMiniServer(
            HttpServer httpServer,
            InetSocketAddress address,
            FileLock instanceLock,
            FileChannel lockChannel) {
        this.httpServer = httpServer;
        this.address = address;
        this.instanceLock = instanceLock;
        this.lockChannel = lockChannel;
    }

    InetSocketAddress getAddress() {
        return address;
    }

    void awaitTermination() throws InterruptedException {
        closedSignal.await();
    }

    synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;

        IOException failure = null;
        try {
            httpServer.stop(0);
        } catch (RuntimeException exception) {
            failure = new IOException("Could not stop the HTTP server", exception);
        }

        try {
            if (instanceLock.isValid()) {
                instanceLock.release();
            }
        } catch (IOException exception) {
            failure = appendFailure(failure, exception);
        }

        try {
            lockChannel.close();
        } catch (IOException exception) {
            failure = appendFailure(failure, exception);
        } finally {
            closedSignal.countDown();
        }

        if (failure != null) {
            throw failure;
        }
    }

    private IOException appendFailure(IOException failure, IOException additionalFailure) {
        if (failure == null) {
            return additionalFailure;
        }
        failure.addSuppressed(additionalFailure);
        return failure;
    }
}
