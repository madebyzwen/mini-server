package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalStopHandlerTest {

    private static final MiniServerStartup.Settings SETTINGS =
            new MiniServerStartup.Settings(2000L, 5L, 500L);

    @TempDir
    Path temporaryDirectory;

    private Path webRoot;
    private final List<StartupResult> results = new ArrayList<StartupResult>();

    @BeforeEach
    void createWebRoot() throws IOException {
        webRoot = temporaryDirectory.resolve("www");
        Files.createDirectories(webRoot.resolve("example"));
    }

    @AfterEach
    void closeServers() {
        for (StartupResult result : results) {
            result.close();
        }
    }

    @Test
    void endpointRejectsUnauthenticatedRequestsAndCorrectTokenStopsServer()
            throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        StartupResult result = own(startup(runtimeDirectory).start());
        RuntimeStateStore.State state = new RuntimeStateStore(runtimeDirectory)
                .readState()
                .get();

        assertEquals(405, request(state.getPort(), "GET", state.getStopToken()));
        assertEquals(403, request(state.getPort(), "POST", null));
        assertEquals(403, request(state.getPort(), "POST", "incorrect-token"));
        assertTrue(result.getRunningServer().isRunning());

        assertEquals(204, request(state.getPort(), "POST", state.getStopToken()));
        awaitStopped(result.getRunningServer());

        assertFalse(result.getRunningServer().isRunning());
        assertFalse(Files.exists(
                runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE)));
    }

    private MiniServerStartup startup(Path runtimeDirectory) {
        return new MiniServerStartup(
                runtimeDirectory,
                webRoot,
                SETTINGS,
                new MiniServerStartup.ServerFactory() {
                    @Override
                    public HttpServer create(InetSocketAddress address) throws IOException {
                        return HttpServer.create(address, 0);
                    }
                },
                new MiniServerStartup.StartupObserver() {
                    @Override
                    public void onStartupLockUnavailable() {
                    }

                    @Override
                    public void onStartupLockAcquired() {
                    }
                });
    }

    private StartupResult own(StartupResult result) {
        results.add(result);
        return result;
    }

    private static int request(int port, String method, String token) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + LocalStopHandler.PATH).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        connection.setInstanceFollowRedirects(false);
        if (token != null) {
            connection.setRequestProperty(LocalStopHandler.TOKEN_HEADER, token);
        }
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static void awaitStopped(RunningMiniServer runningServer)
            throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (runningServer.isRunning() && System.nanoTime() < deadline) {
            Thread.sleep(10L);
        }
    }
}
