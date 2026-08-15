package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpHandler;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Coordinates local single-instance startup and dynamic loopback binding.
 */
public final class MiniServerStartup {

    static final String STARTUP_LOCK_FILE = "startup.lock";
    static final String INSTANCE_LOCK_FILE = "instance.lock";
    static final String INSTANCE_STATE_FILE = "instance.json";
    static final String LOOPBACK_ADDRESS = "127.0.0.1";
    static final int REQUESTED_PORT = 0;

    private static final Settings PRODUCTION_SETTINGS = new Settings(5000L, 25L, 2000L);
    private static final StartupObserver NO_OBSERVER = new StartupObserver() {
        @Override
        public void onStartupLockUnavailable() {
        }

        @Override
        public void onStartupLockAcquired() {
        }
    };
    private static final ServerFactory DEFAULT_SERVER_FACTORY = new ServerFactory() {
        @Override
        public HttpServer create(InetSocketAddress address) throws IOException {
            return HttpServer.create(address, 0);
        }
    };
    private static final BrowserLauncher NO_BROWSER_LAUNCHER = new BrowserLauncher() {
        @Override
        public void open(String url) {
        }
    };

    private final Path injectedRuntimeDirectory;
    private final Path injectedWebRoot;
    private final Settings settings;
    private final ServerFactory serverFactory;
    private final StartupObserver observer;
    private final ConfiguredStartSiteProvider startSites;
    private final BrowserLauncher browserLauncher;

    public MiniServerStartup() {
        this(null, null, PRODUCTION_SETTINGS, DEFAULT_SERVER_FACTORY, NO_OBSERVER,
                new ConfiguredStartSiteProvider(), new WindowsDefaultBrowserLauncher());
    }

    MiniServerStartup(ConfiguredStartSiteProvider startSites) {
        this(null, null, PRODUCTION_SETTINGS, DEFAULT_SERVER_FACTORY, NO_OBSERVER, startSites,
                NO_BROWSER_LAUNCHER);
    }

    MiniServerStartup(
            ConfiguredStartSiteProvider startSites,
            BrowserLauncher browserLauncher) {
        this(null, null, PRODUCTION_SETTINGS, DEFAULT_SERVER_FACTORY, NO_OBSERVER, startSites,
                browserLauncher);
    }

    MiniServerStartup(Path runtimeDirectory, Settings settings) {
        this(runtimeDirectory, null, settings, DEFAULT_SERVER_FACTORY, NO_OBSERVER,
                new ConfiguredStartSiteProvider(), NO_BROWSER_LAUNCHER);
    }

    MiniServerStartup(Path runtimeDirectory, Path webRoot, Settings settings) {
        this(runtimeDirectory, webRoot, settings, DEFAULT_SERVER_FACTORY, NO_OBSERVER,
                new ConfiguredStartSiteProvider(), NO_BROWSER_LAUNCHER);
    }

    MiniServerStartup(
            Path runtimeDirectory,
            Path webRoot,
            Settings settings,
            ServerFactory serverFactory,
            StartupObserver observer) {
        this(runtimeDirectory, webRoot, settings, serverFactory, observer,
                new ConfiguredStartSiteProvider(), NO_BROWSER_LAUNCHER);
    }

    MiniServerStartup(
            Path runtimeDirectory,
            Path webRoot,
            Settings settings,
            ServerFactory serverFactory,
            StartupObserver observer,
            ConfiguredStartSiteProvider startSites) {
        this(runtimeDirectory, webRoot, settings, serverFactory, observer, startSites,
                NO_BROWSER_LAUNCHER);
    }

    MiniServerStartup(
            Path runtimeDirectory,
            Path webRoot,
            Settings settings,
            ServerFactory serverFactory,
            StartupObserver observer,
            ConfiguredStartSiteProvider startSites,
            BrowserLauncher browserLauncher) {
        if (startSites == null || browserLauncher == null) {
            throw new NullPointerException("Start-site dependencies must not be null.");
        }
        this.injectedRuntimeDirectory = runtimeDirectory;
        this.injectedWebRoot = webRoot;
        this.settings = settings;
        this.serverFactory = serverFactory;
        this.observer = observer;
        this.startSites = startSites;
        this.browserLauncher = browserLauncher;
    }

    public StartupResult start() throws StartupException {
        Path runtimeDirectory = resolveRuntimeDirectory();
        RuntimeStateStore stateStore = new RuntimeStateStore(runtimeDirectory);

        FileChannel startupChannel = null;
        FileLock startupLock = null;
        FileChannel instanceChannel = null;
        FileLock instanceLock = null;
        HttpServer httpServer = null;
        RunningMiniServer ownedRunningServer = null;

        try {
            Files.createDirectories(runtimeDirectory);

            startupChannel = openLockChannel(runtimeDirectory.resolve(STARTUP_LOCK_FILE));
            startupLock = acquireStartupLock(startupChannel);
            observer.onStartupLockAcquired();

            instanceChannel = openLockChannel(runtimeDirectory.resolve(INSTANCE_LOCK_FILE));
            instanceLock = tryAcquire(instanceChannel);

            if (instanceLock == null) {
                int activePort = awaitActivePort(stateStore);
                observer.onActiveStateRead();
                instanceLock = tryAcquire(instanceChannel);

                if (instanceLock == null) {
                    release(startupLock);
                    startupLock = null;
                    close(startupChannel);
                    startupChannel = null;
                    close(instanceChannel);
                    instanceChannel = null;
                    return StartupResult.existingInstance(activePort);
                }
            }

            stateStore.invalidate();

            String stopToken = UUID.randomUUID().toString();
            LocalStopHandler localStopHandler = new LocalStopHandler(stopToken);
            HttpHandler rootHandler = createRootHandler(localStopHandler);
            InetAddress loopback = InetAddress.getByName(LOOPBACK_ADDRESS);
            httpServer = serverFactory.create(new InetSocketAddress(loopback, REQUESTED_PORT));
            httpServer.createContext("/", rootHandler);
            httpServer.start();
            validateActiveAddress(httpServer.getAddress(), loopback);

            ownedRunningServer = new RunningMiniServer(
                    httpServer,
                    instanceLock,
                    instanceChannel,
                    stateStore);
            localStopHandler.attach(ownedRunningServer);
            httpServer = null;
            instanceLock = null;
            instanceChannel = null;

            stateStore.writeState(ownedRunningServer.getPort(), stopToken);

            release(startupLock);
            startupLock = null;
            close(startupChannel);
            startupChannel = null;

            StartupResult result = StartupResult.newInstance(ownedRunningServer);
            ownedRunningServer = null;
            return result;
        } catch (StartupException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new StartupException(
                    "Mini Server startup failed: "
                            + ConsoleDiagnostics.failureSummary(exception),
                    exception);
        } finally {
            if (ownedRunningServer != null) {
                ownedRunningServer.close();
            }
            if (httpServer != null) {
                httpServer.stop(0);
            }
            releaseQuietly(instanceLock);
            closeQuietly(instanceChannel);
            releaseQuietly(startupLock);
            closeQuietly(startupChannel);
        }
    }

    private Path resolveRuntimeDirectory() throws StartupException {
        if (injectedRuntimeDirectory != null) {
            return injectedRuntimeDirectory;
        }
        return LocalRuntimeDirectory.resolve();
    }

    private Path resolveWebRoot() throws StartupException {
        if (injectedWebRoot != null) {
            return injectedWebRoot;
        }
        return WebRootResolver.resolve();
    }

    private HttpHandler createRootHandler(LocalStopHandler localStopHandler)
            throws StartupException {
        try {
            Path webRoot = resolveWebRoot();
            StaticFileHandler staticFileHandler = new StaticFileHandler(webRoot);
            PersistenceApiHandler apiHandler = new PersistenceApiHandler(
                    new PersistenceTargetResolver(webRoot),
                    new JsonPersistenceStore());
            return new RootRequestRouter(
                    localStopHandler,
                    apiHandler,
                    staticFileHandler,
                    new WelcomePageHandler(startSites),
                    new StartSiteSelectionHandler(startSites, browserLauncher, System.err));
        } catch (IOException exception) {
            throw new StartupException("The Mini Server web root cannot be accessed.", exception);
        }
    }

    private FileLock acquireStartupLock(FileChannel channel) throws StartupException, IOException {
        long deadline = deadlineAfter(settings.startupLockTimeoutMillis);
        boolean reportedContention = false;

        while (true) {
            FileLock lock = tryAcquire(channel);
            if (lock != null) {
                return lock;
            }

            if (!reportedContention) {
                observer.onStartupLockUnavailable();
                reportedContention = true;
            }

            if (System.nanoTime() >= deadline) {
                throw new StartupException(
                        "Timed out waiting for the local Mini Server startup lock.");
            }
            pause(settings.retryIntervalMillis);
        }
    }

    private int awaitActivePort(RuntimeStateStore stateStore) throws StartupException {
        long deadline = deadlineAfter(settings.activeStateTimeoutMillis);
        IOException lastReadFailure = null;

        while (true) {
            try {
                Optional<RuntimeStateStore.State> state = stateStore.readState();
                if (state.isPresent()) {
                    return state.get().getPort();
                }
            } catch (IOException exception) {
                lastReadFailure = exception;
            }

            if (System.nanoTime() >= deadline) {
                throw new StartupException(
                        "The local Mini Server instance lock is active, but valid runtime state "
                                + "was not available within the bounded wait.",
                        lastReadFailure);
            }
            pause(settings.retryIntervalMillis);
        }
    }

    private static FileChannel openLockChannel(Path lockFile) throws IOException {
        return FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
    }

    private static FileLock tryAcquire(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException exception) {
            return null;
        }
    }

    private static void validateActiveAddress(
            InetSocketAddress address,
            InetAddress expectedLoopback) throws StartupException {
        if (address == null
                || address.getAddress() == null
                || !expectedLoopback.equals(address.getAddress())
                || address.getPort() < 1
                || address.getPort() > 65535) {
            throw new StartupException(
                    "The HTTP server did not publish a usable loopback port.");
        }
    }

    private static long deadlineAfter(long timeoutMillis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    private static void pause(long millis) throws StartupException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new StartupException("Mini Server startup was interrupted.", exception);
        }
    }

    private static void release(FileLock lock) throws IOException {
        lock.release();
    }

    private static void close(FileChannel channel) throws IOException {
        channel.close();
    }

    private static void releaseQuietly(FileLock lock) {
        if (lock == null) {
            return;
        }
        try {
            lock.release();
        } catch (IOException ignored) {
            // Closing the associated channel below provides the remaining cleanup.
        }
    }

    private static void closeQuietly(FileChannel channel) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // Startup has already failed; no further cleanup is possible here.
        }
    }

    interface ServerFactory {
        HttpServer create(InetSocketAddress address) throws IOException;
    }

    interface StartupObserver {
        void onStartupLockUnavailable();

        void onStartupLockAcquired();

        default void onActiveStateRead() {
        }
    }

    static final class Settings {
        private final long startupLockTimeoutMillis;
        private final long retryIntervalMillis;
        private final long activeStateTimeoutMillis;

        Settings(
                long startupLockTimeoutMillis,
                long retryIntervalMillis,
                long activeStateTimeoutMillis) {
            if (startupLockTimeoutMillis < 0L
                    || retryIntervalMillis <= 0L
                    || activeStateTimeoutMillis < 0L) {
                throw new IllegalArgumentException("Startup timing values must be bounded.");
            }
            this.startupLockTimeoutMillis = startupLockTimeoutMillis;
            this.retryIntervalMillis = retryIntervalMillis;
            this.activeStateTimeoutMillis = activeStateTimeoutMillis;
        }
    }
}
