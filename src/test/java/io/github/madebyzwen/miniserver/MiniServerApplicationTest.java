package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniServerApplicationTest {

    private static final MiniServerStartup.Settings SETTINGS =
            new MiniServerStartup.Settings(2000L, 5L, 500L);
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

    private Path webRoot;
    private final List<StartupResult> results = new ArrayList<StartupResult>();

    @BeforeEach
    void createWebRoot() throws IOException {
        webRoot = temporaryDirectory.resolve("www");
        Path exampleDirectory = webRoot.resolve("example");
        Files.createDirectories(exampleDirectory);
        Files.write(
                exampleDirectory.resolve("index.html"),
                "ready".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void closeServers() {
        for (int index = results.size() - 1; index >= 0; index--) {
            results.get(index).close();
        }
    }

    @Test
    void newInstanceLaunchesConfiguredUrlAfterServerAndStateAreReady() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("new-instance");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingOutput output = new RecordingOutput();
        List<String> launchedUrls = new ArrayList<String>();
        BrowserLauncher verifyingLauncher = url -> {
            launchedUrls.add(url);
            int urlPort = new URL(url).getPort();
            assertEquals(
                    urlPort,
                    new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(500);
            connection.setReadTimeout(500);
            assertEquals(200, connection.getResponseCode());
            connection.disconnect();
        };

        StartupResult result = own(application(
                runtimeDirectory,
                serverFactory,
                verifyingLauncher,
                output).start());

        assertTrue(result.isNewInstance());
        assertTrue(result.getRunningServer().isRunning());
        assertEquals(1, serverFactory.creationCount.get());
        assertEquals(1, launchedUrls.size());
        assertEquals(
                "http://127.0.0.1:" + result.getPort() + "/example/",
                launchedUrls.get(0));
        assertTrue(output.standardText().contains(
                "Mini Server started on 127.0.0.1:" + result.getPort() + "."));
    }

    @Test
    void repeatedStartLaunchesExistingPortWithoutCreatingAnotherServer() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("repeated-instance");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher(false);
        RecordingOutput output = new RecordingOutput();
        MiniServerApplication application = application(
                runtimeDirectory,
                serverFactory,
                browserLauncher,
                output);

        StartupResult first = own(application.start());
        StartupResult repeated = own(application.start());

        assertTrue(first.isNewInstance());
        assertTrue(repeated.isExistingInstance());
        assertEquals(first.getPort(), repeated.getPort());
        assertEquals(1, serverFactory.creationCount.get());
        assertEquals(2, browserLauncher.urls.size());
        assertEquals(
                "http://127.0.0.1:" + first.getPort() + "/example/",
                browserLauncher.urls.get(1));
        assertTrue(first.getRunningServer().isRunning());
        assertListenerReachable(first.getPort());
    }

    @Test
    void browserFailureAfterNewStartupLeavesServerAndRuntimeStateValid() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("new-launch-failure");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingOutput output = new RecordingOutput();

        StartupResult result = own(application(
                runtimeDirectory,
                serverFactory,
                new RecordingBrowserLauncher(true),
                output).start());

        String expectedUrl = "http://127.0.0.1:" + result.getPort() + "/example/";
        assertTrue(result.isNewInstance());
        assertTrue(result.getRunningServer().isRunning());
        assertEquals(
                result.getPort(),
                new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
        assertListenerReachable(result.getPort());
        assertTrue(output.errorText().contains("Microsoft Edge could not be opened."));
        assertTrue(output.errorText().contains(expectedUrl));
    }

    @Test
    void browserFailureOnRepeatedStartDoesNotAffectActiveServerOrState() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("repeated-launch-failure");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingOutput firstOutput = new RecordingOutput();
        StartupResult first = own(application(
                runtimeDirectory,
                serverFactory,
                new RecordingBrowserLauncher(false),
                firstOutput).start());
        Path stateFile = runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE);
        byte[] stateBeforeRepeatedStart = Files.readAllBytes(stateFile);

        RecordingOutput repeatedOutput = new RecordingOutput();
        StartupResult repeated = own(application(
                runtimeDirectory,
                serverFactory,
                new RecordingBrowserLauncher(true),
                repeatedOutput).start());

        String expectedUrl = "http://127.0.0.1:" + first.getPort() + "/example/";
        assertTrue(repeated.isExistingInstance());
        assertEquals(first.getPort(), repeated.getPort());
        assertEquals(1, serverFactory.creationCount.get());
        assertArrayEquals(stateBeforeRepeatedStart, Files.readAllBytes(stateFile));
        assertTrue(first.getRunningServer().isRunning());
        assertListenerReachable(first.getPort());
        assertTrue(repeatedOutput.errorText().contains(expectedUrl));
    }

    private MiniServerApplication application(
            Path runtimeDirectory,
            CountingServerFactory serverFactory,
            BrowserLauncher browserLauncher,
            RecordingOutput output) throws Exception {
        MiniServerStartup startup = new MiniServerStartup(
                runtimeDirectory,
                webRoot,
                SETTINGS,
                serverFactory,
                NO_OBSERVER);
        return new MiniServerApplication(
                startup,
                browserLauncher,
                MiniServerApplication.V1_START_TARGET,
                output.standard,
                output.error);
    }

    private StartupResult own(StartupResult result) {
        results.add(result);
        return result;
    }

    private static void assertListenerReachable(int port) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
        }
    }

    private static final class CountingServerFactory implements MiniServerStartup.ServerFactory {

        private final AtomicInteger creationCount = new AtomicInteger();

        @Override
        public HttpServer create(InetSocketAddress address) throws IOException {
            creationCount.incrementAndGet();
            return HttpServer.create(address, 0);
        }
    }

    private static final class RecordingBrowserLauncher implements BrowserLauncher {

        private final boolean fail;
        private final List<String> urls = new ArrayList<String>();

        private RecordingBrowserLauncher(boolean fail) {
            this.fail = fail;
        }

        @Override
        public void open(String url) throws IOException {
            urls.add(url);
            if (fail) {
                throw new IOException("deliberate browser launch failure");
            }
        }
    }

    private static final class RecordingOutput {

        private final ByteArrayOutputStream standardBytes = new ByteArrayOutputStream();
        private final ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        private final PrintStream standard;
        private final PrintStream error;

        private RecordingOutput() throws Exception {
            standard = new PrintStream(standardBytes, true, "UTF-8");
            error = new PrintStream(errorBytes, true, "UTF-8");
        }

        private String standardText() {
            return new String(standardBytes.toByteArray(), StandardCharsets.UTF_8);
        }

        private String errorText() {
            return new String(errorBytes.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
