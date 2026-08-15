package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            try (InputStream responseBody = connection.getInputStream()) {
                while (responseBody.read() != -1) {
                    // Consume the readiness response before disconnecting.
                }
            }
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
    void evaluatesStartSitesOnlyAfterStartupPublishesReadyState() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("provider-after-startup");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher(false);
        RecordingOutput output = new RecordingOutput();
        AtomicInteger providerCalls = new AtomicInteger();
        StartSiteProvider provider = () -> {
            providerCalls.incrementAndGet();
            assertTrue(new RuntimeStateStore(runtimeDirectory).readPort().isPresent());
            return StartSitePlan.applications(Collections.singletonList("example"));
        };

        StartupResult result = own(new MiniServerApplication(
                startup(runtimeDirectory, serverFactory),
                browserLauncher,
                provider,
                output.standard,
                output.error).start());

        assertEquals(1, providerCalls.get());
        assertEquals(
                Collections.singletonList(
                        "http://127.0.0.1:" + result.getPort() + "/example/"),
                browserLauncher.urls);
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
    void rereadsSharedConfigurationOnEveryStartUsingTheExistingPort() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("shared-reread");
        Path sharedConfiguration = temporaryDirectory.resolve("shared-start-sites.txt");
        Path privateConfiguration = temporaryDirectory.resolve("missing-private.txt");
        createApplication("first");
        createApplication("second");
        writeLines(sharedConfiguration, "first", "second");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher(false);
        RecordingOutput output = new RecordingOutput();
        MiniServerApplication application = new MiniServerApplication(
                startup(runtimeDirectory, serverFactory),
                browserLauncher,
                new ConfiguredStartSiteProvider(
                        webRoot,
                        sharedConfiguration,
                        privateConfiguration),
                output.standard,
                output.error);

        StartupResult first = own(application.start());
        writeLines(sharedConfiguration, "second");
        StartupResult repeated = own(application.start());

        String origin = "http://127.0.0.1:" + first.getPort();
        assertTrue(repeated.isExistingInstance());
        assertEquals(first.getPort(), repeated.getPort());
        assertEquals(1, serverFactory.creationCount.get());
        assertEquals(
                Arrays.asList(origin + "/", origin + "/second/"),
                browserLauncher.urls);
    }

    @Test
    void rereadsPrivateConfigurationOnEveryStartUsingTheExistingPort() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("private-reread");
        Path sharedConfiguration = temporaryDirectory.resolve("shared-private-reread.txt");
        Path privateConfiguration = temporaryDirectory.resolve("private-reread.txt");
        createApplication("first");
        createApplication("second");
        writeLines(sharedConfiguration, "first", "second");
        writeLines(privateConfiguration, "first");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher(false);
        RecordingOutput output = new RecordingOutput();
        MiniServerApplication application = new MiniServerApplication(
                startup(runtimeDirectory, serverFactory),
                browserLauncher,
                new ConfiguredStartSiteProvider(
                        webRoot,
                        sharedConfiguration,
                        privateConfiguration),
                output.standard,
                output.error);

        StartupResult first = own(application.start());
        writeLines(privateConfiguration, "second");
        StartupResult repeated = own(application.start());

        String origin = "http://127.0.0.1:" + first.getPort();
        assertTrue(repeated.isExistingInstance());
        assertEquals(first.getPort(), repeated.getPort());
        assertEquals(1, serverFactory.creationCount.get());
        assertEquals(
                Arrays.asList(origin + "/first/", origin + "/second/"),
                browserLauncher.urls);
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
        assertTrue(output.errorText().contains("The default browser could not be opened."));
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
        assertTrue(repeatedOutput.errorText().contains(
                "The default browser could not be opened."));
        assertTrue(repeatedOutput.errorText().contains(expectedUrl));
    }

    @Test
    void providerFailureAfterStartupOpensNothingAndLeavesServerStateValid() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("provider-failure");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher(false);
        RecordingOutput output = new RecordingOutput();
        StartSiteProvider failingProvider = () -> {
            throw new IOException("deliberate configuration failure");
        };

        StartupResult result = own(new MiniServerApplication(
                startup(runtimeDirectory, serverFactory),
                browserLauncher,
                failingProvider,
                output.standard,
                output.error).start());

        assertTrue(result.isNewInstance());
        assertTrue(result.getRunningServer().isRunning());
        assertEquals(
                result.getPort(),
                new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
        assertTrue(browserLauncher.urls.isEmpty());
        assertTrue(output.errorText().contains(
                "Start-site configuration could not be read."));
        assertListenerReachable(result.getPort());
    }

    @Test
    void orderedMultipleTargetsUseTheSameActivePortInCallerOrder() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("multiple-targets");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher(false);
        RecordingOutput output = new RecordingOutput();

        StartupResult result = own(application(
                runtimeDirectory,
                serverFactory,
                browserLauncher,
                Arrays.asList("first", "second", "third"),
                output).start());

        assertEquals(
                Arrays.asList(
                        "http://127.0.0.1:" + result.getPort() + "/first/",
                        "http://127.0.0.1:" + result.getPort() + "/second/",
                        "http://127.0.0.1:" + result.getPort() + "/third/"),
                browserLauncher.urls);
        assertTrue(result.getRunningServer().isRunning());
        assertListenerReachable(result.getPort());
    }

    @Test
    void failureForOneTargetDoesNotPreventLaterTargetsOrInvalidateState()
            throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("isolated-target-failure");
        CountingServerFactory serverFactory = new CountingServerFactory();
        RecordingOutput output = new RecordingOutput();
        List<String> attemptedUrls = new ArrayList<String>();
        BrowserLauncher selectivelyFailingLauncher = url -> {
            attemptedUrls.add(url);
            if (url.endsWith("/second/")) {
                throw new IOException("deliberate middle URL failure");
            }
        };

        StartupResult result = own(application(
                runtimeDirectory,
                serverFactory,
                selectivelyFailingLauncher,
                Arrays.asList("first", "second", "third"),
                output).start());

        String origin = "http://127.0.0.1:" + result.getPort();
        assertEquals(
                Arrays.asList(origin + "/first/", origin + "/second/", origin + "/third/"),
                attemptedUrls);
        assertTrue(output.errorText().contains(
                "The default browser could not be opened."));
        assertTrue(output.errorText().contains(origin + "/second/"));
        assertEquals(
                result.getPort(),
                new RuntimeStateStore(runtimeDirectory).readPort().getAsInt());
        assertTrue(result.getRunningServer().isRunning());
        assertListenerReachable(result.getPort());
    }

    @Test
    void fatalStartupFailureProvidesDetailAndNeverLaunchesBrowser() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("startup-failure");
        String failureDetail = "deliberate listener failure at " + temporaryDirectory;
        MiniServerStartup.ServerFactory failingFactory = address -> {
            throw new IOException(failureDetail);
        };
        RecordingBrowserLauncher browserLauncher = new RecordingBrowserLauncher(false);
        RecordingOutput output = new RecordingOutput();
        AtomicInteger providerCalls = new AtomicInteger();
        MiniServerApplication application = new MiniServerApplication(
                new MiniServerStartup(
                        runtimeDirectory,
                        webRoot,
                        SETTINGS,
                        failingFactory,
                        NO_OBSERVER),
                browserLauncher,
                () -> {
                    providerCalls.incrementAndGet();
                    return StartSitePlan.applications(Collections.singletonList("example"));
                },
                output.standard,
                output.error);

        StartupException failure = assertThrows(StartupException.class, application::start);

        assertTrue(failure.getMessage().contains("IOException"));
        assertTrue(failure.getMessage().contains(failureDetail));
        assertTrue(browserLauncher.urls.isEmpty());
        assertEquals(0, providerCalls.get());
        assertFalse(Files.exists(
                runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE)));
        assertTrue(output.standardText().isEmpty());
        assertTrue(output.errorText().isEmpty());
    }

    @Test
    void wrappedStartupCauseIsRetainedInOneLineConsoleDiagnostic() {
        String internalPath = temporaryDirectory.resolve("restricted/web-root").toString();
        AccessDeniedException cause = new AccessDeniedException(
                internalPath,
                null,
                "access denied\nby the filesystem");
        StartupException failure = new StartupException(
                "The Mini Server web root cannot be accessed.",
                cause);

        String diagnostic = MiniServer.startupFailureMessage(failure);

        assertTrue(diagnostic.contains("StartupException"));
        assertTrue(diagnostic.contains("AccessDeniedException"));
        assertTrue(diagnostic.contains(internalPath));
        assertTrue(diagnostic.contains("access denied by the filesystem"));
        assertFalse(diagnostic.contains("\n"));
        assertFalse(diagnostic.contains("\r"));
    }

    private MiniServerApplication application(
            Path runtimeDirectory,
            CountingServerFactory serverFactory,
            BrowserLauncher browserLauncher,
            RecordingOutput output) throws Exception {
        return new MiniServerApplication(
                startup(runtimeDirectory, serverFactory),
                browserLauncher,
                () -> StartSitePlan.applications(Collections.singletonList("example")),
                output.standard,
                output.error);
    }

    private MiniServerApplication application(
            Path runtimeDirectory,
            CountingServerFactory serverFactory,
            BrowserLauncher browserLauncher,
            Iterable<String> startTargets,
            RecordingOutput output) throws Exception {
        return new MiniServerApplication(
                startup(runtimeDirectory, serverFactory),
                browserLauncher,
                () -> {
                    List<String> sites = new ArrayList<String>();
                    for (String startTarget : startTargets) {
                        sites.add(startTarget);
                    }
                    return sites.isEmpty()
                            ? StartSitePlan.none(null)
                            : StartSitePlan.applications(sites);
                },
                output.standard,
                output.error);
    }

    private MiniServerStartup startup(
            Path runtimeDirectory,
            CountingServerFactory serverFactory) {
        return new MiniServerStartup(
                runtimeDirectory,
                webRoot,
                SETTINGS,
                serverFactory,
                NO_OBSERVER);
    }

    private Path createApplication(String name) throws IOException {
        Path applicationDirectory = webRoot.resolve(name);
        Files.createDirectories(applicationDirectory);
        Files.write(
                applicationDirectory.resolve("index.html"),
                name.getBytes(StandardCharsets.UTF_8));
        return applicationDirectory;
    }

    private static void writeLines(Path file, String... lines) throws IOException {
        Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
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
