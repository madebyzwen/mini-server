package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orchestrates local startup and the independent browser-launch request.
 */
final class MiniServerApplication {

    static final String V1_START_TARGET = "/example/";

    private final MiniServerStartup startup;
    private final BrowserLauncher browserLauncher;
    private final List<LocalServerUrl> localServerUrls;
    private final PrintStream output;
    private final PrintStream errorOutput;

    MiniServerApplication(
            MiniServerStartup startup,
            BrowserLauncher browserLauncher,
            String startTarget,
            PrintStream output,
            PrintStream errorOutput) {
        this(
                startup,
                browserLauncher,
                Collections.singletonList(startTarget),
                output,
                errorOutput);
    }

    MiniServerApplication(
            MiniServerStartup startup,
            BrowserLauncher browserLauncher,
            Iterable<String> startTargets,
            PrintStream output,
            PrintStream errorOutput) {
        if (startup == null
                || browserLauncher == null
                || startTargets == null
                || output == null
                || errorOutput == null) {
            throw new NullPointerException("Application dependencies must not be null.");
        }
        this.startup = startup;
        this.browserLauncher = browserLauncher;
        List<LocalServerUrl> urls = new ArrayList<LocalServerUrl>();
        for (String startTarget : startTargets) {
            urls.add(new LocalServerUrl(startTarget));
        }
        this.localServerUrls = Collections.unmodifiableList(urls);
        this.output = output;
        this.errorOutput = errorOutput;
    }

    StartupResult start() throws StartupException {
        StartupResult result = startup.start();

        if (result.isExistingInstance()) {
            output.println(
                    "Mini Server is already running locally on port " + result.getPort() + ".");
        } else {
            output.println("Mini Server started on 127.0.0.1:" + result.getPort() + ".");
        }

        for (LocalServerUrl localServerUrl : localServerUrls) {
            String url = localServerUrl.forPort(result.getPort());
            try {
                browserLauncher.open(url);
            } catch (IOException | RuntimeException exception) {
                errorOutput.println(
                        "The default browser could not be opened. "
                                + "Open this URL manually: "
                                + url);
            }
        }

        return result;
    }
}
