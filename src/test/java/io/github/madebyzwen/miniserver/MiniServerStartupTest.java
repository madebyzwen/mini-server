package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.OptionalInt;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniServerStartupTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void freshInstanceUsesOperatingSystemPortAndIpv4Loopback() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");

        try (StartupResult result = new MiniServerStartup().start(installationRoot)) {
            assertTrue(result.isNewInstance());
            assertTrue(result.getPort() > 0);
            assertEquals(result.getPort(), result.getAddress().getPort());
            assertEquals("127.0.0.1", result.getAddress().getAddress().getHostAddress());
            assertTcpConnection(result.getAddress());

            RuntimeStateStore stateStore = new RuntimeStateStore(
                    installationRoot.resolve(".runtime"));
            assertEquals(result.getPort(), stateStore.readPort().getAsInt());
        }
    }

    @Test
    void repeatedStartReturnsExistingPortWithoutOwningAnotherServer() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");

        try (StartupResult first = new MiniServerStartup().start(installationRoot)) {
            try (StartupResult firstRepeated = new MiniServerStartup().start(installationRoot);
                 StartupResult secondRepeated = new MiniServerStartup().start(installationRoot)) {
                assertFalse(firstRepeated.isNewInstance());
                assertFalse(secondRepeated.isNewInstance());
                assertEquals(first.getPort(), firstRepeated.getPort());
                assertEquals(first.getPort(), secondRepeated.getPort());
            }

            assertTcpConnection(first.getAddress());
        }
    }

    @Test
    void staleStateCannotBeReturnedDuringProtectedStartupPublication() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");
        Path runtimeDirectory = installationRoot.resolve(".runtime");
        Files.createDirectories(runtimeDirectory);
        writeState(runtimeDirectory, "{\"port\":1}");

        CountDownLatch ownerHasBothLocks = new CountDownLatch(1);
        CountDownLatch allowOwnerToPublish = new CountDownLatch(1);
        CountDownLatch contenderReachedStartupLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        StartupResult owner = null;
        StartupResult repeated = null;

        try {
            MiniServerStartup ownerStartup = new MiniServerStartup(
                    10L,
                    5_000L,
                    new StartupObserverAdapter() {
                        @Override
                        public void instanceLockAcquired() throws IOException {
                            ownerHasBothLocks.countDown();
                            awaitLatch(allowOwnerToPublish, "owner publication release");
                        }
                    });
            Future<StartupResult> ownerFuture = executor.submit(
                    () -> ownerStartup.start(installationRoot));

            assertTrue(ownerHasBothLocks.await(5L, TimeUnit.SECONDS));
            assertEquals(1, new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());

            MiniServerStartup contenderStartup = new MiniServerStartup(
                    10L,
                    5_000L,
                    new StartupObserverAdapter() {
                        @Override
                        public void startupLockContended() {
                            contenderReachedStartupLock.countDown();
                        }
                    });
            Future<StartupResult> contenderFuture = executor.submit(
                    () -> contenderStartup.start(installationRoot));

            assertTrue(contenderReachedStartupLock.await(5L, TimeUnit.SECONDS));
            assertFalse(contenderFuture.isDone());
            allowOwnerToPublish.countDown();

            owner = ownerFuture.get(10L, TimeUnit.SECONDS);
            repeated = contenderFuture.get(10L, TimeUnit.SECONDS);

            assertTrue(owner.isNewInstance());
            assertFalse(repeated.isNewInstance());
            assertEquals(owner.getPort(), repeated.getPort());
            assertNotEquals(1, repeated.getPort());
        } finally {
            allowOwnerToPublish.countDown();
            closeQuietly(repeated);
            closeQuietly(owner);
            executor.shutdownNow();
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        }
    }

    @Test
    void contenderBecomesNewOwnerWhenPreviousOwnerEndsBeforeFinalValidation() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");
        CountDownLatch stateWasRead = new CountDownLatch(1);
        CountDownLatch allowFinalValidation = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        StartupResult owner = new MiniServerStartup().start(installationRoot);
        StartupResult replacement = null;
        ServerSocket previousPortGuard = null;

        try {
            int previousPort = owner.getPort();
            MiniServerStartup contenderStartup = new MiniServerStartup(
                    10L,
                    5_000L,
                    new StartupObserverAdapter() {
                        @Override
                        public void existingStateRead() throws IOException {
                            stateWasRead.countDown();
                            awaitLatch(allowFinalValidation, "final instance-lock validation");
                        }
                    });
            Future<StartupResult> contenderFuture = executor.submit(
                    () -> contenderStartup.start(installationRoot));

            assertTrue(stateWasRead.await(5L, TimeUnit.SECONDS));
            owner.close();

            previousPortGuard = new ServerSocket();
            previousPortGuard.setReuseAddress(true);
            previousPortGuard.bind(new InetSocketAddress(
                    InetAddress.getByName("127.0.0.1"),
                    previousPort));
            allowFinalValidation.countDown();

            replacement = contenderFuture.get(10L, TimeUnit.SECONDS);
            assertTrue(replacement.isNewInstance());
            assertNotEquals(previousPort, replacement.getPort());
            assertEquals(
                    replacement.getPort(),
                    new RuntimeStateStore(installationRoot.resolve(".runtime")).readPort().getAsInt());
            assertTcpConnection(replacement.getAddress());
        } finally {
            allowFinalValidation.countDown();
            if (previousPortGuard != null) {
                previousPortGuard.close();
            }
            closeQuietly(replacement);
            closeQuietly(owner);
            executor.shutdownNow();
            executor.awaitTermination(5L, TimeUnit.SECONDS);
        }
    }

    @Test
    void startupLockIsReleasedAfterStatePublication() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");

        try (StartupResult active = new MiniServerStartup().start(installationRoot);
             FileChannel channel = FileChannel.open(
                     installationRoot.resolve(".runtime").resolve("startup.lock"),
                     StandardOpenOption.WRITE)) {
            FileLock startupLock = channel.tryLock();
            try {
                assertNotNull(startupLock);
                assertTrue(active.isNewInstance());
                assertTcpConnection(active.getAddress());
            } finally {
                if (startupLock != null) {
                    startupLock.release();
                }
            }
        }
    }

    @Test
    void startupLockContentionFailsWithinBoundedWait() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");
        Path runtimeDirectory = installationRoot.resolve(".runtime");
        Files.createDirectories(runtimeDirectory);

        try (FileChannel channel = FileChannel.open(
                runtimeDirectory.resolve("startup.lock"),
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            IOException exception = assertThrows(
                    IOException.class,
                    () -> new MiniServerStartup(10L, 100L).start(installationRoot));

            assertTrue(exception.getMessage().contains("startup coordination lock"));
        }
    }

    @Test
    void differentInstallationRootsOwnIndependentServers() throws Exception {
        Path firstRoot = temporaryDirectory.resolve("first-installation");
        Path secondRoot = temporaryDirectory.resolve("second-installation");

        try (StartupResult first = new MiniServerStartup().start(firstRoot);
             StartupResult second = new MiniServerStartup().start(secondRoot)) {
            assertTrue(first.isNewInstance());
            assertTrue(second.isNewInstance());
            assertNotEquals(first.getPort(), second.getPort());
            assertTcpConnection(first.getAddress());
            assertTcpConnection(second.getAddress());
        }
    }

    @Test
    void staleStateIsReplacedAfterFreeLockIsAcquired() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");
        Path runtimeDirectory = installationRoot.resolve(".runtime");
        Files.createDirectories(runtimeDirectory);
        writeState(runtimeDirectory, "{\"port\":1}");

        StartupResult first = new MiniServerStartup().start(installationRoot);
        int firstPort = first.getPort();
        first.close();

        assertTrue(first.isNewInstance());
        assertNotEquals(1, firstPort);
        assertEquals(firstPort, new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());

        try (StartupResult next = new MiniServerStartup().start(installationRoot)) {
            assertTrue(next.isNewInstance());
            assertTrue(next.getPort() > 0);
        }
    }

    @Test
    void malformedStateIsNotAcceptedWhileInstallationLockIsOwned() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");
        Path runtimeDirectory = installationRoot.resolve(".runtime");
        Files.createDirectories(runtimeDirectory);
        writeState(runtimeDirectory, "{not-valid-json");

        Path lockFile = runtimeDirectory.resolve("instance.lock");
        try (FileChannel channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            IOException exception = assertThrows(
                    IOException.class,
                    () -> new MiniServerStartup(10L, 100L).start(installationRoot));

            assertTrue(exception.getMessage().contains("valid runtime state"));
        }
    }

    @Test
    void runtimeStateReaderRejectsInvalidPortsAndShapes() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("installation").resolve(".runtime");
        Files.createDirectories(runtimeDirectory);
        RuntimeStateStore stateStore = new RuntimeStateStore(runtimeDirectory);
        String[] invalidStates = {
                "",
                "{",
                "{'port':123}",
                "{port:123}",
                "[]",
                "{}",
                "{\"port\":null}",
                "{\"port\":true}",
                "{\"port\":\"123\"}",
                "{\"port\":0}",
                "{\"port\":-1}",
                "{\"port\":65536}",
                "{\"port\":1.5}"
        };

        for (String invalidState : invalidStates) {
            writeState(runtimeDirectory, invalidState);
            OptionalInt port = stateStore.readPort();
            assertFalse(port.isPresent(), "Unexpected valid state: " + invalidState);
        }
    }

    @Test
    void ownedResourcesCanBeClosedRepeatedly() throws Exception {
        Path installationRoot = temporaryDirectory.resolve("installation");
        StartupResult result = new MiniServerStartup().start(installationRoot);

        result.close();
        result.close();

        try (StartupResult restarted = new MiniServerStartup().start(installationRoot)) {
            assertTrue(restarted.isNewInstance());
            assertTrue(restarted.getPort() > 0);
        }
    }

    private void writeState(Path runtimeDirectory, String content) throws IOException {
        Files.write(
                runtimeDirectory.resolve("instance.json"),
                content.getBytes(StandardCharsets.UTF_8));
    }

    private void assertTcpConnection(InetSocketAddress address) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(address, 1_000);
            assertTrue(socket.isConnected());
        }
    }

    private static void awaitLatch(CountDownLatch latch, String description) throws IOException {
        try {
            if (!latch.await(5L, TimeUnit.SECONDS)) {
                throw new IOException("Timed out waiting for " + description);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for " + description, exception);
        }
    }

    private void closeQuietly(StartupResult result) {
        if (result == null) {
            return;
        }
        try {
            result.close();
        } catch (IOException ignored) {
        }
    }

    private abstract static class StartupObserverAdapter
            implements MiniServerStartup.StartupObserver {
        @Override
        public void startupLockContended() throws IOException {
        }

        @Override
        public void instanceLockAcquired() throws IOException {
        }

        @Override
        public void existingStateRead() throws IOException {
        }
    }
}
