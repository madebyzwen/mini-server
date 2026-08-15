package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Authenticates graceful stop requests on the existing loopback listener.
 */
final class LocalStopHandler implements HttpHandler {

    static final String PATH = "/__miniserver/stop";
    static final String TOKEN_HEADER = "X-MiniServer-Token";

    private final String stopToken;
    private volatile RunningMiniServer runningServer;

    LocalStopHandler(String stopToken) {
        if (stopToken == null) {
            throw new NullPointerException("Stop token must not be null.");
        }
        this.stopToken = stopToken;
    }

    void attach(RunningMiniServer runningServer) {
        if (runningServer == null) {
            throw new NullPointerException("Running server must not be null.");
        }
        if (this.runningServer != null) {
            throw new IllegalStateException("Running server is already attached.");
        }
        this.runningServer = runningServer;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            respond(exchange, 405);
            return;
        }

        String suppliedToken = exchange.getRequestHeaders().getFirst(TOKEN_HEADER);
        if (!stopToken.equals(suppliedToken)) {
            respond(exchange, 403);
            return;
        }

        final RunningMiniServer server = runningServer;
        if (server == null) {
            respond(exchange, 503);
            return;
        }

        respond(exchange, 204);
        Thread shutdownThread = new Thread(new Runnable() {
            @Override
            public void run() {
                server.close();
            }
        }, "mini-server-stop");
        shutdownThread.setDaemon(true);
        shutdownThread.start();
    }

    private static void respond(HttpExchange exchange, int status) throws IOException {
        try {
            exchange.sendResponseHeaders(status, -1L);
        } finally {
            exchange.close();
        }
    }
}
