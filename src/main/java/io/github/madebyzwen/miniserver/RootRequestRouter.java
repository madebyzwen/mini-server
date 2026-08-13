package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Separates the central persistence API namespace from normal static requests.
 */
final class RootRequestRouter implements HttpHandler {

    private static final String API_COMPONENT = "api";

    private final HttpHandler apiHandler;
    private final HttpHandler staticFileHandler;

    RootRequestRouter(HttpHandler apiHandler, HttpHandler staticFileHandler) {
        this.apiHandler = apiHandler;
        this.staticFileHandler = staticFileHandler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getRawPath();
        if (isApiRequest(rawPath)) {
            apiHandler.handle(exchange);
        } else {
            staticFileHandler.handle(exchange);
        }
    }

    static boolean isApiRequest(String rawPath) {
        String decodedPath = UrlPathDecoder.decode(rawPath);
        return hasApiShape(decodedPath) || hasApiShape(rawPath);
    }

    private static boolean hasApiShape(String path) {
        if (path == null || !path.startsWith("/")) {
            return false;
        }

        String[] components = path.substring(1).split("/", -1);
        boolean canonicalApiNamespace =
                components.length > 1 && API_COMPONENT.equals(components[1]);
        boolean rejectedScopeFirstApiNamespace = components.length > 2
                && PersistenceScope.fromPathComponent(components[1]) != null
                && API_COMPONENT.equals(components[2]);
        return canonicalApiNamespace || rejectedScopeFirstApiNamespace;
    }
}
