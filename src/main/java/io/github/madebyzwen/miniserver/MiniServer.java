package io.github.madebyzwen.miniserver;

/**
 * Minimal executable entry point for local Mini Server startup.
 */
public final class MiniServer {

    private MiniServer() {
    }

    public static void main(String[] args) {
        try {
            StartupResult result = new MiniServerStartup().start();
            if (result.isExistingInstance()) {
                System.out.println(
                        "Mini Server is already running locally on port " + result.getPort() + ".");
                return;
            }

            final RunningMiniServer runningServer = result.getRunningServer();
            System.out.println("Mini Server started on 127.0.0.1:" + result.getPort() + ".");

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
            System.err.println("Mini Server could not start: " + exception.getMessage());
            System.exit(1);
        }
    }
}
