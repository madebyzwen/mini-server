package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates local startup and the independent browser-launch request.
 */
final class MiniServerApplication {

    private final MiniServerStartup startup;
    private final BrowserLauncher browserLauncher;
    private final StartSiteProvider startSiteProvider;
    private final PrintStream output;
    private final PrintStream errorOutput;

    MiniServerApplication(
            MiniServerStartup startup,
            BrowserLauncher browserLauncher,
            StartSiteProvider startSiteProvider,
            PrintStream output,
            PrintStream errorOutput) {
        if (startup == null
                || browserLauncher == null
                || startSiteProvider == null
                || output == null
                || errorOutput == null) {
            throw new NullPointerException("Application dependencies must not be null.");
        }
        this.startup = startup;
        this.browserLauncher = browserLauncher;
        this.startSiteProvider = startSiteProvider;
        this.output = output;
        this.errorOutput = errorOutput;
    }

    StartupResult start() throws StartupException {
        return start(Action.NORMAL);
    }

    StartupResult configure() throws StartupException {
        return start(Action.CONFIGURE);
    }

    private StartupResult start(Action action) throws StartupException {
        StartupResult result = startup.start();

        if (result.isExistingInstance()) {
            output.println(
                    "Mini Server is already running locally on port " + result.getPort() + ".");
        } else {
            output.println("Mini Server started on 127.0.0.1:" + result.getPort() + ".");
        }

        List<String> urls = new ArrayList<String>();
        if (action == Action.CONFIGURE) {
            urls.add(LocalServerUrl.rootForPort(result.getPort()));
        } else {
            try {
                StartSitePlan plan = startSiteProvider.planStartSites();
                if (plan.getDiagnostic() != null) {
                    errorOutput.println(plan.getDiagnostic());
                }
                if (plan.getKind() == StartSitePlan.Kind.ROOT) {
                    urls.add(LocalServerUrl.rootForPort(result.getPort()));
                } else if (plan.getKind() == StartSitePlan.Kind.APPLICATIONS) {
                    for (String startSite : plan.getSites()) {
                        urls.add(new LocalServerUrl(startSite).forPort(result.getPort()));
                    }
                }
            } catch (IOException | RuntimeException exception) {
                errorOutput.println(
                        "Start-site configuration could not be read. "
                                + "Opening the recovery page instead.");
                urls.add(LocalServerUrl.rootForPort(result.getPort()));
            }
        }

        for (String url : urls) {
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

    private enum Action {
        NORMAL,
        CONFIGURE
    }
}
