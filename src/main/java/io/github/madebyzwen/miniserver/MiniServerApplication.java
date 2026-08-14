package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.io.PrintStream;

/**
 * Orchestrates local startup and the independent browser-launch request.
 */
final class MiniServerApplication {

    static final String V1_START_TARGET = "/example/";

    private final MiniServerStartup startup;
    private final BrowserLauncher browserLauncher;
    private final LocalServerUrl localServerUrl;
    private final PrintStream output;
    private final PrintStream errorOutput;

    MiniServerApplication(
            MiniServerStartup startup,
            BrowserLauncher browserLauncher,
            String startTarget,
            PrintStream output,
            PrintStream errorOutput) {
        if (startup == null
                || browserLauncher == null
                || output == null
                || errorOutput == null) {
            throw new NullPointerException("Application dependencies must not be null.");
        }
        this.startup = startup;
        this.browserLauncher = browserLauncher;
        this.localServerUrl = new LocalServerUrl(startTarget);
        this.output = output;
        this.errorOutput = errorOutput;
    }

    StartupResult start() throws StartupException {
        StartupResult result = startup.start();
        String url = localServerUrl.forPort(result.getPort());

        if (result.isExistingInstance()) {
            output.println(
                    "Mini Server is already running locally on port " + result.getPort() + ".");
        } else {
            output.println("Mini Server started on 127.0.0.1:" + result.getPort() + ".");
        }

        try {
            browserLauncher.open(url);
        } catch (IOException | RuntimeException exception) {
            errorOutput.println(
                    "Microsoft Edge could not be opened. Open this URL manually: " + url);
        }

        return result;
    }
}
