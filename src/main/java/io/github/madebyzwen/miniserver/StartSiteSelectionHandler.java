package io.github.madebyzwen.miniserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Replaces and immediately applies the current-user start-site selection. */
final class StartSiteSelectionHandler implements HttpHandler {

    static final String PATH = "/__miniserver/start-sites";

    private final ConfiguredStartSiteProvider startSites;
    private final BrowserLauncher browserLauncher;
    private final PrintStream errorOutput;

    StartSiteSelectionHandler(ConfiguredStartSiteProvider startSites) {
        this(startSites, new BrowserLauncher() {
            @Override
            public void open(String url) {
            }
        }, System.err);
    }

    StartSiteSelectionHandler(
            ConfiguredStartSiteProvider startSites,
            BrowserLauncher browserLauncher,
            PrintStream errorOutput) {
        if (startSites == null || browserLauncher == null || errorOutput == null) {
            throw new NullPointerException("Start-site handler dependencies must not be null.");
        }
        this.startSites = startSites;
        this.browserLauncher = browserLauncher;
        this.errorOutput = errorOutput;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                respondEmpty(exchange, 405);
                return;
            }
            if (!isJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                respondEmpty(exchange, 415);
                return;
            }
            List<String> requested;
            try {
                requested = parse(exchange.getRequestBody());
            } catch (InvalidRequestException exception) {
                respondEmpty(exchange, 400);
                return;
            }
            if (requested.isEmpty()) {
                respondEmpty(exchange, 400);
                return;
            }

            final int activePort;
            try {
                activePort = activePort(exchange);
            } catch (RuntimeException exception) {
                respondEmpty(exchange, 500);
                return;
            }

            try {
                List<String> normalized = startSites.saveSelection(requested);
                List<String> targets = targets(normalized, activePort);
                openAdditionalTargets(targets);
                respondSuccess(exchange, normalized, targets);
            } catch (ConfiguredStartSiteProvider.EmptySelectionException exception) {
                respondEmpty(exchange, 400);
            } catch (ConfiguredStartSiteProvider.SelectionConflictException exception) {
                respondEmpty(exchange, 409);
            } catch (ConfiguredStartSiteProvider.SharedConfigurationUnavailableException exception) {
                respondEmpty(exchange, 503);
            } catch (IOException | RuntimeException exception) {
                respondEmpty(exchange, 500);
            }
        } finally {
            exchange.getRequestBody().close();
        }
    }

    private static int activePort(HttpExchange exchange) {
        if (exchange.getLocalAddress() == null) {
            throw new IllegalStateException("The active local address is unavailable.");
        }
        int port = exchange.getLocalAddress().getPort();
        LocalServerUrl.rootForPort(port);
        return port;
    }

    private static List<String> targets(List<String> sites, int activePort) {
        List<String> targets = new ArrayList<String>();
        for (String site : sites) {
            targets.add(new LocalServerUrl(site).forPort(activePort));
        }
        return targets;
    }

    private void openAdditionalTargets(List<String> targets) {
        for (int index = 1; index < targets.size(); index++) {
            String target = targets.get(index);
            try {
                browserLauncher.open(target);
            } catch (IOException | RuntimeException exception) {
                errorOutput.println(
                        "The default browser could not be opened. Open this URL manually: "
                                + target);
            }
        }
    }

    private static List<String> parse(InputStream input) throws IOException, InvalidRequestException {
        byte[] bytes = readAll(input);
        final String body;
        try {
            body = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new InvalidRequestException();
        }
        try {
            JsonReader reader = new JsonReader(new StringReader(body));
            reader.setStrictness(Strictness.STRICT);
            if (reader.peek() != JsonToken.BEGIN_OBJECT) {
                throw new InvalidRequestException();
            }
            reader.beginObject();
            if (!reader.hasNext() || !"sites".equals(reader.nextName())
                    || reader.peek() != JsonToken.BEGIN_ARRAY) {
                throw new InvalidRequestException();
            }
            List<String> sites = new ArrayList<String>();
            reader.beginArray();
            while (reader.hasNext()) {
                if (reader.peek() != JsonToken.STRING) {
                    throw new InvalidRequestException();
                }
                sites.add(reader.nextString());
            }
            reader.endArray();
            if (reader.hasNext()) {
                throw new InvalidRequestException();
            }
            reader.endObject();
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                throw new InvalidRequestException();
            }
            return sites;
        } catch (IOException | IllegalStateException exception) {
            throw new InvalidRequestException();
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private static boolean isJson(String contentType) {
        if (contentType == null) {
            return false;
        }
        int parameter = contentType.indexOf(';');
        String mediaType = (parameter < 0 ? contentType : contentType.substring(0, parameter)).trim();
        return "application/json".equalsIgnoreCase(mediaType);
    }

    private static void respondSuccess(
            HttpExchange exchange,
            List<String> sites,
            List<String> targets) throws IOException {
        JsonArray siteValues = new JsonArray();
        for (String site : sites) {
            siteValues.add(site);
        }
        JsonArray targetValues = new JsonArray();
        for (String target : targets) {
            targetValues.add(target);
        }
        JsonObject response = new JsonObject();
        response.add("sites", siteValues);
        response.add("targets", targetValues);
        byte[] body = response.toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void respondEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1L);
        exchange.close();
    }

    private static final class InvalidRequestException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
