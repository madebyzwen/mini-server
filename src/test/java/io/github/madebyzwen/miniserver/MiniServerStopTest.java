package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiniServerStopTest {

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
    void stopWithoutRunningServerIsIdempotentAndCreatesNothing() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("missing-runtime");
        RecordingOutput output = new RecordingOutput();

        int first = MiniServer.stop(runtimeDirectory, output.standard, output.error);
        int second = MiniServer.stop(runtimeDirectory, output.standard, output.error);

        assertEquals(0, first);
        assertEquals(0, second);
        assertFalse(Files.exists(runtimeDirectory));
        assertTrue(output.standardText().contains("Mini Server is not running."));
        assertTrue(output.errorText().isEmpty());
    }

    @Test
    void commandDispatchSupportsNormalConfigureAndStopWithClearUsage() {
        assertEquals(MiniServer.Command.START, MiniServer.commandFor(new String[0]));
        assertEquals(MiniServer.Command.CONFIGURE,
                MiniServer.commandFor(new String[] {"configure"}));
        assertEquals(MiniServer.Command.STOP,
                MiniServer.commandFor(new String[] {"stop"}));
        assertEquals(MiniServer.Command.INVALID,
                MiniServer.commandFor(new String[] {"unknown"}));
        assertEquals(MiniServer.Command.INVALID,
                MiniServer.commandFor(new String[] {"configure", "stop"}));
        assertEquals("Usage: MiniServer [configure|stop]", MiniServer.USAGE);
    }

    @Test
    void stopCommandUsesActiveListenerAndAllowsFreshDynamicStartup() throws Exception {
        Path runtimeDirectory = temporaryDirectory.resolve("stop-and-restart");
        MiniServerStartup startup = startup(runtimeDirectory);
        StartupResult first = own(startup.start());
        RecordingOutput output = new RecordingOutput();

        int exitCode = MiniServer.stop(runtimeDirectory, output.standard, output.error);

        assertEquals(0, exitCode, output.errorText());
        assertFalse(first.getRunningServer().isRunning());
        assertFalse(Files.exists(
                runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE)));
        assertTrue(output.standardText().contains("Mini Server stopped."));
        assertTrue(output.errorText().isEmpty());

        StartupResult restarted = own(startup.start());

        assertTrue(restarted.isNewInstance());
        assertTrue(restarted.getPort() >= 1 && restarted.getPort() <= 65535);
        assertTrue(restarted.getRunningServer().isRunning());
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
