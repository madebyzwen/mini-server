package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

public final class MiniServerStartup {
    public static final String LOOPBACK_ADDRESS = "127.0.0.1";

    private static final int REQUESTED_PORT = 0;
    private static final long DEFAULT_RETRY_INTERVAL_MILLIS = 100L;
    private static final long DEFAULT_MAXIMUM_WAIT_MILLIS = 5_000L;
    private static final StartupObserver NO_OP_OBSERVER = new StartupObserver() {
        @Override
        public void startupLockContended() {
        }

        @Override
        public void instanceLockAcquired() {
        }

        @Override
        public void existingStateRead() {
        }
    };

    private final long retryIntervalMillis;
    private final long maximumWaitMillis;
    private final StartupObserver observer;

    public MiniServerStartup() {
        this(DEFAULT_RETRY_INTERVAL_MILLIS, DEFAULT_MAXIMUM_WAIT_MILLIS, NO_OP_OBSERVER);
    }

    MiniServerStartup(long retryIntervalMillis, long maximumWaitMillis) {
        this(retryIntervalMillis, maximumWaitMillis, NO_OP_OBSERVER);
    }

    MiniServerStartup(
            long retryIntervalMillis,
            long maximumWaitMillis,
            StartupObserver observer) {
        if (retryIntervalMillis <= 0L || maximumWaitMillis <= 0L) {
            throw new IllegalArgumentException("Retry timing must be greater than zero");
        }
        if (observer == null) {
            throw new IllegalArgumentException("Startup observer must not be null");
        }
        this.retryIntervalMillis = retryIntervalMillis;
        this.maximumWaitMillis = maximumWaitMillis;
        this.observer = observer;
    }

    public StartupResult start(Path installationRoot) throws IOException {
        if (installationRoot == null) {
            throw new IllegalArgumentException("Installation root must not be null");
        }

        Path normalizedRoot = installationRoot.toAbsolutePath().normalize();
        Path runtimeDirectory = normalizedRoot.resolve(".runtime");
        createRuntimeDirectory(runtimeDirectory);

        RuntimeStateStore stateStore = new RuntimeStateStore(runtimeDirectory);
        LockOwnership startupOwnership = acquireStartupLock(runtimeDirectory.resolve("startup.lock"));
        StartupResult result = null;
        boolean startupLockReleased = false;

        try {
            result = startWhileCoordinated(runtimeDirectory, stateStore);
            releaseOwnedLock(startupOwnership, "startup coordination lock");
            startupLockReleased = true;
            return result;
        } catch (IOException failure) {
            closeResult(result, failure);
            closeOwnedLock(startupOwnership, failure);
            startupLockReleased = true;
            throw failure;
        } catch (RuntimeException exception) {
            IOException failure = new IOException("Mini Server startup failed", exception);
            closeResult(result, failure);
            closeOwnedLock(startupOwnership, failure);
            startupLockReleased = true;
            throw failure;
        } finally {
            if (!startupLockReleased) {
                closeOwnedLock(startupOwnership, null);
            }
        }
    }

    private StartupResult startWhileCoordinated(
            Path runtimeDirectory,
            RuntimeStateStore stateStore) throws IOException {
        Path instanceLockFile = runtimeDirectory.resolve("instance.lock");
        LockOwnership instanceOwnership = tryAcquireInstanceLock(instanceLockFile);
        if (instanceOwnership != null) {
            return startNewInstance(stateStore, instanceOwnership);
        }

        OptionalInt activePort = stateStore.readPort();
        observer.existingStateRead();

        instanceOwnership = tryAcquireInstanceLock(instanceLockFile);
        if (instanceOwnership != null) {
            return startNewInstance(stateStore, instanceOwnership);
        }

        if (activePort.isPresent()) {
            return StartupResult.existing(activePort.getAsInt());
        }

        throw new IOException(
                "Another Mini Server instance owns the installation lock, but valid runtime state "
                        + "is not available");
    }

    private LockOwnership acquireStartupLock(Path startupLockFile) throws IOException {
        FileChannel channel = openLockChannel(startupLockFile, "startup coordination");
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(maximumWaitMillis);

        try {
            while (true) {
                FileLock lock = tryAcquireFileLock(channel, "startup coordination lock");
                if (lock != null) {
                    return new LockOwnership(channel, lock);
                }

                observer.startupLockContended();
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    throw new IOException(
                            "Could not acquire the startup coordination lock within "
                                    + maximumWaitMillis + " ms");
                }
                sleepBeforeRetry(remainingNanos);
            }
        } catch (IOException failure) {
            closeChannel(channel, failure);
            throw failure;
        } catch (RuntimeException exception) {
            IOException failure = new IOException(
                    "Could not acquire the startup coordination lock", exception);
            closeChannel(channel, failure);
            throw failure;
        }
    }

    private LockOwnership tryAcquireInstanceLock(Path instanceLockFile) throws IOException {
        FileChannel channel = openLockChannel(instanceLockFile, "installation instance");

        try {
            FileLock lock = tryAcquireFileLock(channel, "installation instance lock");
            if (lock == null) {
                releaseChannel(channel, "installation instance lock channel");
                return null;
            }
            return new LockOwnership(channel, lock);
        } catch (IOException failure) {
            closeChannel(channel, failure);
            throw failure;
        } catch (RuntimeException exception) {
            IOException failure = new IOException("Could not acquire the installation lock", exception);
            closeChannel(channel, failure);
            throw failure;
        }
    }

    private FileLock tryAcquireFileLock(FileChannel channel, String description) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            return null;
        } catch (IOException | RuntimeException exception) {
            throw new IOException("Could not acquire the " + description, exception);
        }
    }

    private StartupResult startNewInstance(
            RuntimeStateStore stateStore,
            LockOwnership instanceOwnership) throws IOException {
        HttpServer httpServer = null;

        try {
            observer.instanceLockAcquired();

            try {
                stateStore.invalidate();
            } catch (IOException exception) {
                throw new IOException("Could not invalidate stale runtime state", exception);
            }

            InetAddress loopback = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
            try {
                httpServer = HttpServer.create(new InetSocketAddress(loopback, REQUESTED_PORT), 0);
                httpServer.start();
            } catch (IOException | RuntimeException exception) {
                throw new IOException("Could not start the HTTP server on 127.0.0.1 with port 0", exception);
            }

            InetSocketAddress activeAddress = httpServer.getAddress();
            int assignedPort = activeAddress == null ? 0 : activeAddress.getPort();
            if (assignedPort < 1 || assignedPort > 65_535) {
                throw new IOException("The operating system did not assign a usable HTTP port");
            }

            try {
                stateStore.publish(assignedPort);
            } catch (IOException exception) {
                throw new IOException("Could not publish runtime state", exception);
            }

            RunningMiniServer runningServer = new RunningMiniServer(
                    httpServer,
                    activeAddress,
                    instanceOwnership.lock,
                    instanceOwnership.channel);
            return StartupResult.started(runningServer);
        } catch (IOException failure) {
            releaseAfterFailedStartup(httpServer, instanceOwnership, failure);
            throw failure;
        } catch (RuntimeException exception) {
            IOException failure = new IOException("Mini Server startup failed", exception);
            releaseAfterFailedStartup(httpServer, instanceOwnership, failure);
            throw failure;
        }
    }

    private void createRuntimeDirectory(Path runtimeDirectory) throws IOException {
        try {
            Files.createDirectories(runtimeDirectory);
        } catch (IOException | SecurityException exception) {
            throw new IOException("Could not create runtime directory: " + runtimeDirectory, exception);
        }
    }

    private FileChannel openLockChannel(Path lockFile, String description) throws IOException {
        try {
            return FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        } catch (IOException | SecurityException exception) {
            throw new IOException("Could not open " + description + " lock file: " + lockFile, exception);
        }
    }

    private void sleepBeforeRetry(long remainingNanos) throws IOException {
        long remainingMillis = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        long sleepMillis = Math.min(retryIntervalMillis, remainingMillis);
        try {
            Thread.sleep(sleepMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for the startup coordination lock", exception);
        }
    }

    private void releaseAfterFailedStartup(
            HttpServer httpServer,
            LockOwnership instanceOwnership,
            IOException failure) {
        if (httpServer != null) {
            try {
                httpServer.stop(0);
            } catch (RuntimeException exception) {
                failure.addSuppressed(exception);
            }
        }
        closeOwnedLock(instanceOwnership, failure);
    }

    private void releaseOwnedLock(LockOwnership ownership, String description) throws IOException {
        IOException failure = null;

        try {
            if (ownership.lock.isValid()) {
                ownership.lock.release();
            }
        } catch (IOException exception) {
            failure = exception;
        }

        try {
            ownership.channel.close();
        } catch (IOException exception) {
            if (failure == null) {
                failure = exception;
            } else {
                failure.addSuppressed(exception);
            }
        }

        if (failure != null) {
            throw new IOException("Could not release the " + description, failure);
        }
    }

    private void releaseChannel(FileChannel channel, String description) throws IOException {
        try {
            channel.close();
        } catch (IOException exception) {
            throw new IOException("Could not close the " + description, exception);
        }
    }

    private void closeOwnedLock(LockOwnership ownership, Throwable failure) {
        try {
            if (ownership.lock.isValid()) {
                ownership.lock.release();
            }
        } catch (IOException exception) {
            addSuppressed(failure, exception);
        }
        closeChannel(ownership.channel, failure);
    }

    private void closeChannel(FileChannel channel, Throwable failure) {
        try {
            channel.close();
        } catch (IOException exception) {
            addSuppressed(failure, exception);
        }
    }

    private void closeResult(StartupResult result, Throwable failure) {
        if (result == null) {
            return;
        }

        try {
            result.close();
        } catch (IOException exception) {
            addSuppressed(failure, exception);
        }
    }

    private void addSuppressed(Throwable failure, Throwable additionalFailure) {
        if (failure != null) {
            failure.addSuppressed(additionalFailure);
        }
    }

    interface StartupObserver {
        void startupLockContended() throws IOException;

        void instanceLockAcquired() throws IOException;

        void existingStateRead() throws IOException;
    }

    private static final class LockOwnership {
        private final FileChannel channel;
        private final FileLock lock;

        private LockOwnership(FileChannel channel, FileLock lock) {
            this.channel = channel;
            this.lock = lock;
        }
    }
}
