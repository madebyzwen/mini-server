package io.github.madebyzwen.miniserver;

/**
 * Minimal executable entry point for local Mini Server startup.
 */
public final class MiniServer {

    private MiniServer() {
    }

    public static void main(String[] args) {
        try {
            StartupResult result = new MiniServerApplication(
                    new MiniServerStartup(),
                    new EdgeBrowserLauncher(),
                    MiniServerApplication.V1_START_TARGET,
                    System.out,
                    System.err).start();
            if (result.isExistingInstance()) {
                return;
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
        } catch (StartupException exception) {
            System.err.println(startupFailureMessage(exception));
            System.exit(1);
        }
    }

    static String startupFailureMessage(StartupException exception) {
        return "Mini Server could not start: " + ConsoleDiagnostics.failureSummary(exception);
    }
}
