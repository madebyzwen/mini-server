package io.github.madebyzwen.miniserver;

import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Replaces the current-user start-site selection after Shared normalization. */
final class StartSiteSelectionHandler implements HttpHandler {

    static final String PATH = "/__miniserver/start-sites";

    private final ConfiguredStartSiteProvider startSites;

    StartSiteSelectionHandler(ConfiguredStartSiteProvider startSites) {
        this.startSites = startSites;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Allow", "POST");
                respond(exchange, 405);
                return;
            }
            if (!isJson(exchange.getRequestHeaders().getFirst("Content-Type"))) {
                respond(exchange, 415);
                return;
            }
            List<String> requested;
            try {
                requested = parse(exchange.getRequestBody());
            } catch (InvalidRequestException exception) {
                respond(exchange, 400);
                return;
            }
            try {
                startSites.saveSelection(requested);
                respond(exchange, 204);
            } catch (ConfiguredStartSiteProvider.SharedConfigurationUnavailableException exception) {
                respond(exchange, 503);
            } catch (IOException | RuntimeException exception) {
                respond(exchange, 500);
            }
        } finally {
            exchange.getRequestBody().close();
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

    private static void respond(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1L);
        exchange.close();
    }

    private static final class InvalidRequestException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
