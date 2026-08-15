package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Minimal executable entry point for local Mini Server startup.
 */
public final class MiniServer {

    private static final int EXIT_SUCCESS = 0;
    private static final int EXIT_FAILURE = 1;
    private static final int STOP_CONNECT_TIMEOUT_MILLIS = 2000;
    private static final int STOP_READ_TIMEOUT_MILLIS = 2000;
    private static final long STOP_WAIT_TIMEOUT_MILLIS = 5000L;
    private static final long STOP_RETRY_MILLIS = 25L;

    private MiniServer() {
    }

    public static void main(String[] args) {
        int exitCode;
        if (args.length == 0) {
            exitCode = start();
        } else if (args.length == 1 && "stop".equals(args[0])) {
            exitCode = stop();
        } else {
            System.err.println("Usage: MiniServer [stop]");
            exitCode = EXIT_FAILURE;
        }

        if (exitCode != EXIT_SUCCESS) {
            System.exit(exitCode);
        }
    }

    private static int start() {
        try {
            ConfiguredStartSiteProvider startSites = new ConfiguredStartSiteProvider();
            StartupResult result = new MiniServerApplication(
                    new MiniServerStartup(startSites),
                    new WindowsDefaultBrowserLauncher(),
                    startSites,
                    System.out,
                    System.err).start();
            if (result.isExistingInstance()) {
                return EXIT_SUCCESS;
            }

            final RunningMiniServer runningServer = result.getRunningServer();
            Runtime.getRuntime().addShutdownHook(
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            runningServer.close();
                        }
                    }, "mini-server-shutdown"));

            try {
                runningServer.awaitTermination();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runningServer.close();
            }
            return EXIT_SUCCESS;
        } catch (StartupException exception) {
            System.err.println(startupFailureMessage(exception));
            return EXIT_FAILURE;
        }
    }

    private static int stop() {
        try {
            return stop(LocalRuntimeDirectory.resolve(), System.out, System.err);
        } catch (StartupException exception) {
            System.err.println(
                    "Mini Server could not be stopped: "
                            + ConsoleDiagnostics.failureSummary(exception));
            return EXIT_FAILURE;
        }
    }

    static int stop(Path runtimeDirectory, PrintStream output, PrintStream errorOutput) {
        if (runtimeDirectory == null || output == null || errorOutput == null) {
            throw new NullPointerException("Stop command dependencies must not be null.");
        }

        try {
            if (!Files.isDirectory(runtimeDirectory)) {
                output.println("Mini Server is not running.");
                return EXIT_SUCCESS;
            }

            RuntimeStateStore stateStore = new RuntimeStateStore(runtimeDirectory);
            Optional<RuntimeStateStore.State> storedState = stateStore.readState();
            Path instanceLockFile = runtimeDirectory.resolve(
                    MiniServerStartup.INSTANCE_LOCK_FILE);
            boolean instanceActive = isInstanceActive(instanceLockFile);
            if (!storedState.isPresent()) {
                if (instanceActive) {
                    errorOutput.println(
                            "Mini Server cannot be stopped because local runtime state is invalid.");
                    return EXIT_FAILURE;
                }
                stateStore.invalidate();
                output.println("Mini Server is not running.");
                return EXIT_SUCCESS;
            }
            if (!instanceActive) {
                stateStore.invalidate();
                output.println("Mini Server is not running.");
                return EXIT_SUCCESS;
            }

            RuntimeStateStore.State state = storedState.get();
            int responseCode = requestStop(state);
            if (responseCode != HttpURLConnection.HTTP_NO_CONTENT) {
                errorOutput.println(
                        "Mini Server stop request was rejected with HTTP status "
                                + responseCode
                                + ".");
                return EXIT_FAILURE;
            }

            if (!awaitInstanceRelease(instanceLockFile)) {
                errorOutput.println("Timed out waiting for Mini Server to stop.");
                return EXIT_FAILURE;
            }
            stateStore.invalidate();
            output.println("Mini Server stopped.");
            return EXIT_SUCCESS;
        } catch (IOException | RuntimeException exception) {
            errorOutput.println(
                    "Mini Server could not be stopped: "
                            + ConsoleDiagnostics.failureSummary(exception));
            return EXIT_FAILURE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            errorOutput.println("Mini Server stop was interrupted.");
            return EXIT_FAILURE;
        }
    }

    private static int requestStop(RuntimeStateStore.State state) throws IOException {
        URL stopUrl = new URL(
                "http://"
                        + MiniServerStartup.LOOPBACK_ADDRESS
                        + ":"
                        + state.getPort()
                        + LocalStopHandler.PATH);
        HttpURLConnection connection = (HttpURLConnection) stopUrl.openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(STOP_CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(STOP_READ_TIMEOUT_MILLIS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty(LocalStopHandler.TOKEN_HEADER, state.getStopToken());
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(0);
        try {
            return connection.getResponseCode();
        } finally {
            connection.disconnect();
        }
    }

    private static boolean awaitInstanceRelease(Path instanceLockFile)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime()
                + TimeUnit.MILLISECONDS.toNanos(STOP_WAIT_TIMEOUT_MILLIS);
        while (isInstanceActive(instanceLockFile)) {
            if (System.nanoTime() >= deadline) {
                return false;
            }
            Thread.sleep(STOP_RETRY_MILLIS);
        }
        return true;
    }

    private static boolean isInstanceActive(Path instanceLockFile) throws IOException {
        if (!Files.isRegularFile(instanceLockFile)) {
            return false;
        }
        try (FileChannel channel = FileChannel.open(
                instanceLockFile,
                StandardOpenOption.WRITE)) {
            try {
                FileLock lock = channel.tryLock();
                if (lock == null) {
                    return true;
                }
                lock.release();
                return false;
            } catch (OverlappingFileLockException exception) {
                return true;
            }
        }
    }

    static String startupFailureMessage(StartupException exception) {
        return "Mini Server could not start: " + ConsoleDiagnostics.failureSummary(exception);
    }
}
