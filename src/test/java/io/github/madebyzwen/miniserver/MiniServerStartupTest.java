package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniServerStartupTest {

    private static final MiniServerStartup.Settings NORMAL_SETTINGS =
            new MiniServerStartup.Settings(2000L, 5L, 500L);
    private static final MiniServerStartup.Settings SHORT_SETTINGS =
            new MiniServerStartup.Settings(100L, 5L, 100L);
    private static final MiniServerStartup.StartupObserver NO_OBSERVER =
            new MiniServerStartup.StartupObserver() {
                @Override
                public void onStartupLockUnavailable() {
                }

                @Override
                public void onStartupLockAcquired() {
                }
            };

    @TempDir
    Path temporaryDirectory;

    private Path testWebRoot;

    private final List<StartupResult> results = new ArrayList<StartupResult>();
    private final List<ExecutorService> executors = new ArrayList<ExecutorService>();

    @BeforeEach
    void createIsolatedWebRoot() throws IOException {
        testWebRoot = temporaryDirectory.resolve("www");
        Files.createDirectories(testWebRoot);
    }

    @AfterEach
    void closeOwnedResources() throws InterruptedException {
        for (int index = results.size() - 1; index >= 0; index--) {
            results.get(index).close();
        }
        for (ExecutorService executor : executors) {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
        }
    }

    @Test
    void freshStartupUsesLoopbackDynamicPortPublishesStateAndOwnsLock() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("fresh");
        RecordingServerFactory serverFactory = new RecordingServerFactory();

        StartupResult result = own(startup(runtimeDirectory, NORMAL_SETTINGS, serverFactory).start());

        assertTrue(result.isNewInstance());
        assertEquals(1, serverFactory.getCreationCount());
        InetSocketAddress requestedAddress = serverFactory.getRequestedAddresses().get(0);
        assertEquals(InetAddress.getByName("127.0.0.1"), requestedAddress.getAddress());
        assertEquals(0, requestedAddress.getPort());

        RunningMiniServer runningServer = result.getRunningServer();
        assertEquals(InetAddress.getByName("127.0.0.1"), runningServer.getAddress().getAddress());
        assertTrue(result.getPort() >= 1 && result.getPort() <= 65535);
        assertEquals(runningServer.getAddress().getPort(), result.getPort());
        assertListenerReachable(result.getPort());

        assertEquals(
                result.getPort(),
                new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
        assertFalse(canAcquire(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));
    }

    @Test
    void newInstanceRegistersStaticHandlerBeforeServingRequests() throws Exception {
        Path indexFile = testWebRoot.resolve("example/index.html");
        Files.createDirectories(indexFile.getParent());
        Files.write(indexFile, "startup integration".getBytes(StandardCharsets.UTF_8));
        StartupResult result = own(
                startup(
                        temporaryDirectory.resolve("static-integration"),
                        NORMAL_SETTINGS,
                        new RecordingServerFactory()).start());

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + result.getPort() + "/example/").openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        try {
            assertEquals(200, connection.getResponseCode());
            assertEquals("startup integration", readText(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void newInstanceRegistersPersistenceApiBeforeServingRequests() throws Exception {
        Files.createDirectories(testWebRoot.resolve("example"));
        StartupResult result = own(
                startup(
                        temporaryDirectory.resolve("api-integration"),
                        NORMAL_SETTINGS,
                        new RecordingServerFactory()).start());

        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:"
                        + result.getPort()
                        + "/example/api/shared/readAll").openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        try {
            assertEquals(200, connection.getResponseCode());
            assertEquals("application/json; charset=utf-8", connection.getContentType());
            assertEquals("{}", readText(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    @Test
    void unavailableWebRootFailsNewStartupAndReleasesCoordinationResources() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("missing-web-root");
        Path missingWebRoot = temporaryDirectory.resolve("does-not-exist");
        RecordingServerFactory serverFactory = new RecordingServerFactory();

        assertThrows(
                StartupException.class,
                () -> new MiniServerStartup(
                        runtimeDirectory,
                        missingWebRoot,
                        NORMAL_SETTINGS,
                        serverFactory,
                        NO_OBSERVER).start());

        assertEquals(0, serverFactory.getCreationCount());
        assertFalse(Files.exists(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE)));
        assertTrue(canAcquire(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));
    }

    @Test
    void repeatedStartDoesNotResolveOrRequireAnotherWebRoot() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("repeated-with-missing-web-root");
        RecordingServerFactory serverFactory = new RecordingServerFactory();
        StartupResult first = own(startup(runtimeDirectory, NORMAL_SETTINGS, serverFactory).start());

        StartupResult repeated = own(new MiniServerStartup(
                runtimeDirectory,
                temporaryDirectory.resolve("missing-repeated-web-root"),
                NORMAL_SETTINGS,
                serverFactory,
                NO_OBSERVER).start());

        assertTrue(repeated.isExistingInstance());
        assertEquals(first.getPort(), repeated.getPort());
        assertEquals(1, serverFactory.getCreationCount());
    }

    @Test
    void repeatedStartReturnsActivePortWithoutCreatingOrStoppingServer() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("repeated");
        RecordingServerFactory serverFactory = new RecordingServerFactory();
        MiniServerStartup startup = startup(runtimeDirectory, NORMAL_SETTINGS, serverFactory);
        StartupResult first = own(startup.start());

        StartupResult repeated = own(startup.start());

        assertTrue(first.isNewInstance());
        assertTrue(repeated.isExistingInstance());
        assertEquals(first.getPort(), repeated.getPort());
        assertEquals(1, serverFactory.getCreationCount());
        assertThrows(IllegalStateException.class, repeated::getRunningServer);

        repeated.close();
        assertTrue(first.getRunningServer().isRunning());
        assertFalse(canAcquire(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));
        assertListenerReachable(first.getPort());
    }

    @Test
    void repeatedStartBecomesNewInstanceWhenActiveServerStopsAfterStateRead() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("shutdown-race");
        RecordingServerFactory serverFactory = new RecordingServerFactory();
        StartupResult first = own(
                startup(runtimeDirectory, NORMAL_SETTINGS, serverFactory).start());
        CountDownLatch candidateStateRead = new CountDownLatch(1);
        CountDownLatch allowFinalLockVerification = new CountDownLatch(1);
        MiniServerStartup.StartupObserver observer = new MiniServerStartup.StartupObserver() {
            @Override
            public void onStartupLockUnavailable() {
            }

            @Override
            public void onStartupLockAcquired() {
            }

            @Override
            public void onActiveStateRead() {
                candidateStateRead.countDown();
                awaitLatch(allowFinalLockVerification);
            }
        };

        ExecutorService executor = own(Executors.newSingleThreadExecutor());
        Future<StartupResult> repeatedFuture = executor.submit(
                () -> new MiniServerStartup(
                        runtimeDirectory,
                        testWebRoot,
                        NORMAL_SETTINGS,
                        serverFactory,
                        observer).start());
        assertTrue(candidateStateRead.await(2L, TimeUnit.SECONDS));
        assertEquals(
                first.getPort(),
                new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());

        first.close();
        assertTrue(canAcquire(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));
        allowFinalLockVerification.countDown();

        StartupResult replacement = own(repeatedFuture.get(2L, TimeUnit.SECONDS));

        assertTrue(replacement.isNewInstance());
        assertEquals(2, serverFactory.getCreationCount());
        InetSocketAddress replacementRequest = serverFactory.getRequestedAddresses().get(1);
        assertEquals(InetAddress.getByName("127.0.0.1"), replacementRequest.getAddress());
        assertEquals(0, replacementRequest.getPort());
        assertEquals(replacement.getRunningServer().getAddress().getPort(), replacement.getPort());
        assertEquals(
                replacement.getPort(),
                new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
        assertFalse(canAcquire(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));
    }

    @Test
    void staleStateIsReplacedByTheActualNewServerPort() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("stale-state");
        Files.createDirectories(runtimeDirectory);

        InetAddress loopback = InetAddress.getByName("127.0.0.1");
        try (ServerSocket reservedPort = new ServerSocket(0, 1, loopback)) {
            int stalePort = reservedPort.getLocalPort();
            new RuntimeStateStore(runtimeDirectory).writePort(stalePort);

            StartupResult result = own(
                    startup(runtimeDirectory, NORMAL_SETTINGS, new RecordingServerFactory()).start());

            assertTrue(result.isNewInstance());
            assertNotEquals(stalePort, result.getPort());
            assertEquals(
                    result.getPort(),
                    new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
        }
    }

    @Test
    void staleInstanceLockFileWithoutOwnershipDoesNotBlockStartup() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("stale-lock");
        Files.createDirectories(runtimeDirectory);
        Files.createFile(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE));

        StartupResult result = own(
                startup(runtimeDirectory, NORMAL_SETTINGS, new RecordingServerFactory()).start());

        assertTrue(result.isNewInstance());
    }

    @Test
    void concurrentStartsProduceExactlyOneServerAndOneRepeatedResult() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("race");
        RecordingServerFactory serverFactory = new RecordingServerFactory();
        CountDownLatch firstHasStartupLock = new CountDownLatch(1);
        CountDownLatch releaseFirstStartup = new CountDownLatch(1);
        CountDownLatch secondObservedContention = new CountDownLatch(1);

        MiniServerStartup.StartupObserver firstObserver = new MiniServerStartup.StartupObserver() {
            @Override
            public void onStartupLockUnavailable() {
            }

            @Override
            public void onStartupLockAcquired() {
                firstHasStartupLock.countDown();
                awaitLatch(releaseFirstStartup);
            }
        };
        MiniServerStartup.StartupObserver secondObserver = new MiniServerStartup.StartupObserver() {
            @Override
            public void onStartupLockUnavailable() {
                secondObservedContention.countDown();
            }

            @Override
            public void onStartupLockAcquired() {
            }
        };

        ExecutorService executor = own(Executors.newFixedThreadPool(2));
        Future<StartupResult> firstFuture = executor.submit(
                () -> new MiniServerStartup(
                        runtimeDirectory,
                        testWebRoot,
                        NORMAL_SETTINGS,
                        serverFactory,
                        firstObserver).start());
        assertTrue(firstHasStartupLock.await(2L, TimeUnit.SECONDS));

        Future<StartupResult> secondFuture = executor.submit(
                () -> new MiniServerStartup(
                        runtimeDirectory,
                        testWebRoot,
                        NORMAL_SETTINGS,
                        serverFactory,
                        secondObserver).start());
        assertTrue(secondObservedContention.await(2L, TimeUnit.SECONDS));
        releaseFirstStartup.countDown();

        StartupResult first = own(firstFuture.get(2L, TimeUnit.SECONDS));
        StartupResult second = own(secondFuture.get(2L, TimeUnit.SECONDS));

        assertEquals(1, countNewInstances(first, second));
        assertEquals(1, countExistingInstances(first, second));
        assertEquals(first.getPort(), second.getPort());
        assertEquals(1, serverFactory.getCreationCount());
    }

    @Test
    void startupLockContentionFailsWithinBoundWithoutStartingServer() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("startup-contention");
        Files.createDirectories(runtimeDirectory);
        Path startupLockPath = runtimeDirectory.resolve(MiniServerStartup.STARTUP_LOCK_FILE);
        RecordingServerFactory serverFactory = new RecordingServerFactory();

        try (FileChannel channel = openLockChannel(startupLockPath);
             FileLock ignored = channel.lock()) {
            assertTimeout(
                    Duration.ofSeconds(1L),
                    () -> assertThrows(
                            StartupException.class,
                            () -> startup(runtimeDirectory, SHORT_SETTINGS, serverFactory).start()));
        }

        assertEquals(0, serverFactory.getCreationCount());
    }

    @Test
    void activeInstanceLockWithInvalidStateFailsWithoutCompetingServer() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("invalid-active-state");
        Files.createDirectories(runtimeDirectory);
        Files.write(
                runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE),
                Collections.singletonList("{\"port\":0}"),
                StandardCharsets.UTF_8);
        RecordingServerFactory serverFactory = new RecordingServerFactory();

        try (FileChannel channel = openLockChannel(
                runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE));
             FileLock heldInstanceLock = channel.lock()) {
            assertTimeout(
                    Duration.ofSeconds(1L),
                    () -> assertThrows(
                            StartupException.class,
                            () -> startup(runtimeDirectory, SHORT_SETTINGS, serverFactory).start()));
            assertTrue(heldInstanceLock.isValid());
        }

        assertEquals(0, serverFactory.getCreationCount());
    }

    @Test
    void closingServerReleasesLockAndAllowsSubsequentStartup() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("restart");
        MiniServerStartup startup =
                startup(runtimeDirectory, NORMAL_SETTINGS, new RecordingServerFactory());
        StartupResult first = own(startup.start());
        first.close();

        assertTrue(canAcquire(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));

        StartupResult second = own(startup.start());

        assertTrue(second.isNewInstance());
        assertEquals(
                second.getPort(),
                new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
    }

    @Test
    void independentRuntimeContextsCanRunIndependentServers() throws Exception {
        Path firstRuntime = temporaryDirectory.resolve("context-one");
        Path secondRuntime = temporaryDirectory.resolve("context-two");

        StartupResult first = own(
                startup(firstRuntime, NORMAL_SETTINGS, new RecordingServerFactory()).start());
        StartupResult second = own(
                startup(secondRuntime, NORMAL_SETTINGS, new RecordingServerFactory()).start());

        assertTrue(first.isNewInstance());
        assertTrue(second.isNewInstance());
        assertEquals(first.getPort(), new RuntimeStateStore(firstRuntime).readPort().getAsInt());
        assertEquals(second.getPort(), new RuntimeStateStore(secondRuntime).readPort().getAsInt());
        assertFalse(canAcquire(firstRuntime.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));
        assertFalse(canAcquire(secondRuntime.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));
    }

    @Test
    void failedServerCreationLeavesNoPublishedStateOrOwnedLock() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("failure-cleanup");
        Files.createDirectories(runtimeDirectory);
        new RuntimeStateStore(runtimeDirectory).writePort(12345);
        MiniServerStartup.ServerFactory failingFactory = address -> {
            throw new IOException("deliberate test failure");
        };

        assertThrows(
                StartupException.class,
                () -> startup(runtimeDirectory, NORMAL_SETTINGS, failingFactory).start());

        assertFalse(Files.exists(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE)));
        assertTrue(canAcquire(runtimeDirectory.resolve(MiniServerStartup.INSTANCE_LOCK_FILE)));

        StartupResult subsequent = own(
                startup(runtimeDirectory, NORMAL_SETTINGS, new RecordingServerFactory()).start());
        assertTrue(subsequent.isNewInstance());
    }

    private MiniServerStartup startup(
            Path runtimeDirectory,
            MiniServerStartup.Settings settings,
            MiniServerStartup.ServerFactory serverFactory) {
        return new MiniServerStartup(
                runtimeDirectory,
                testWebRoot,
                settings,
                serverFactory,
                NO_OBSERVER);
    }

    private StartupResult own(StartupResult result) {
        results.add(result);
        return result;
    }

    private ExecutorService own(ExecutorService executor) {
        executors.add(executor);
        return executor;
    }

    private static int countNewInstances(StartupResult... startupResults) {
        int count = 0;
        for (StartupResult result : startupResults) {
            if (result.isNewInstance()) {
                count++;
            }
        }
        return count;
    }

    private static int countExistingInstances(StartupResult... startupResults) {
        int count = 0;
        for (StartupResult result : startupResults) {
            if (result.isExistingInstance()) {
                count++;
            }
        }
        return count;
    }

    private static void assertListenerReachable(int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
        }
    }

    private static String readText(InputStream input) throws IOException {
        try (InputStream closeableInput = input;
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[256];
            int read;
            while ((read = closeableInput.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static boolean canAcquire(Path lockFile) throws IOException {
        try (FileChannel channel = openLockChannel(lockFile)) {
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    return false;
                }
                lock.release();
                return true;
            } catch (OverlappingFileLockException exception) {
                return false;
            }
        }
    }

    private static FileChannel openLockChannel(Path lockFile) throws IOException {
        return FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(2L, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for deterministic startup coordination.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Startup coordination was interrupted.", exception);
        }
    }

    private static final class RecordingServerFactory implements MiniServerStartup.ServerFactory {

        private final AtomicInteger creationCount = new AtomicInteger();
        private final List<InetSocketAddress> requestedAddresses =
                Collections.synchronizedList(new ArrayList<InetSocketAddress>());

        @Override
        public HttpServer create(InetSocketAddress address) throws IOException {
            creationCount.incrementAndGet();
            requestedAddresses.add(address);
            return HttpServer.create(address, 0);
        }

        int getCreationCount() {
            return creationCount.get();
        }

        List<InetSocketAddress> getRequestedAddresses() {
            return requestedAddresses;
        }
    }
}
