package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.nio.file.Path;

public final class MiniServer {
    private MiniServer() {
    }

    public static void main(String[] args) {
        StartupResult result = null;

        try {
            Path installationRoot = InstallationRoot.resolve();
            result = new MiniServerStartup().start(installationRoot);

            if (!result.isNewInstance()) {
                System.out.println("Mini Server is already running on 127.0.0.1:" + result.getPort());
                return;
            }

            final StartupResult activeResult = result;
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        activeResult.close();
                    } catch (IOException exception) {
                        System.err.println("Mini Server shutdown failed: " + exception.getMessage());
                    }
                }
            }, "mini-server-shutdown"));

            System.out.println("Mini Server started on 127.0.0.1:" + result.getPort());
            result.awaitTermination();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (Exception exception) {
            System.err.println("Mini Server startup failed: " + exception.getMessage());
            closeAfterFailure(result);
            System.exit(1);
        } finally {
            closeAfterFailure(result);
        }
    }

    private static void closeAfterFailure(StartupResult result) {
        if (result == null) {
            return;
        }

        try {
            result.close();
        } catch (IOException exception) {
            System.err.println("Mini Server shutdown failed: " + exception.getMessage());
        }
    }
}
