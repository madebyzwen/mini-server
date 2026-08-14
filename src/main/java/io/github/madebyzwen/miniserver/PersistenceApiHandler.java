package io.github.madebyzwen.miniserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.MalformedJsonException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;

/**
 * Implements the central, explicitly scoped JSON persistence HTTP API.
 */
final class PersistenceApiHandler implements HttpHandler {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";
    private static final String API_COMPONENT = "api";

    private static final String READ = "read";
    private static final String READ_ALL = "readAll";
    private static final String WRITE = "write";
    private static final String REMOVE = "remove";
    private static final String CLEAR = "clear";

    private final PersistenceTargetResolver targetResolver;
    private final PersistenceStore persistenceStore;
    private final ConsoleDiagnostics diagnostics;

    PersistenceApiHandler(
            PersistenceTargetResolver targetResolver,
            PersistenceStore persistenceStore) {
        this(
                targetResolver,
                persistenceStore,
                new ConsoleDiagnostics(System.err));
    }

    PersistenceApiHandler(
            PersistenceTargetResolver targetResolver,
            PersistenceStore persistenceStore,
            ConsoleDiagnostics diagnostics) {
        this.targetResolver = targetResolver;
        this.persistenceStore = persistenceStore;
        this.diagnostics = diagnostics;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ApiResponse response;
        try {
            response = process(exchange);
        } catch (ApiProblem problem) {
            response = errorResponse(problem);
        } catch (IOException | RuntimeException exception) {
            diagnostics.report("API request", exception);
            response = errorResponse(ApiProblem.internalError());
        } finally {
            closeRequestBody(exchange);
        }
        sendResponse(exchange, response);
    }

    private ApiResponse process(HttpExchange exchange) throws IOException, ApiProblem {
        String rawPath = exchange.getRequestURI().getRawPath();
        ApiRoute route = parseRoute(rawPath);
        String requiredMethod = requiredMethod(route.operation);
        if (!requiredMethod.equals(exchange.getRequestMethod())) {
            throw ApiProblem.methodNotAllowed(requiredMethod);
        }

        Optional<ResolvedPersistenceTarget> resolved;
        try {
            resolved = targetResolver.resolve(rawPath);
        } catch (IOException exception) {
            diagnostics.report("persistence target resolution", exception);
            throw ApiProblem.persistenceError(
                    isModifyingOperation(route.operation)
                            ? "Write failed"
                            : "Persistence read failed.");
        }
        if (!resolved.isPresent()) {
            throw ApiProblem.notFound();
        }
        ResolvedPersistenceTarget target = resolved.get();

        try {
            if (READ.equals(route.operation)) {
                String section = requiredSection(exchange.getRequestURI().getRawQuery());
                return ApiResponse.json(200, persistenceStore.read(target, section));
            }
            if (READ_ALL.equals(route.operation)) {
                return ApiResponse.json(200, persistenceStore.readAll(target));
            }
            if (WRITE.equals(route.operation)) {
                requireJsonContentType(exchange.getRequestHeaders());
                JsonObject sections = parseWriteBody(exchange.getRequestBody());
                persistenceStore.write(target, sections);
                return ApiResponse.noContent();
            }
            if (REMOVE.equals(route.operation)) {
                String section = requiredSection(exchange.getRequestURI().getRawQuery());
                persistenceStore.remove(target, section);
                return ApiResponse.noContent();
            }

            persistenceStore.clear(target);
            return ApiResponse.noContent();
        } catch (SectionNotFoundException exception) {
            throw ApiProblem.sectionNotFound();
        } catch (PersistenceException exception) {
            diagnostics.report("persistence operation", exception);
            throw persistenceProblem(exception, route.operation);
        }
    }

    private static ApiRoute parseRoute(String rawPath) throws ApiProblem {
        String decodedPath = UrlPathDecoder.decode(rawPath);
        if (decodedPath == null
                || UrlPathDecoder.containsEncodedPathSeparator(rawPath)
                || !decodedPath.startsWith("/")
                || decodedPath.startsWith("//")
                || decodedPath.indexOf('\\') >= 0
                || decodedPath.indexOf(':') >= 0
                || UrlPathDecoder.containsControlCharacter(decodedPath)) {
            throw ApiProblem.badRequest();
        }

        String[] components = decodedPath.substring(1).split("/", -1);
        if (components.length != 4
                || components[0].isEmpty()
                || !API_COMPONENT.equals(components[1])
                || ".".equals(components[0])
                || "..".equals(components[0])) {
            throw ApiProblem.badRequest();
        }

        if (PersistenceScope.fromPathComponent(components[2]) == null) {
            throw ApiProblem.badRequest();
        }
        if (requiredMethod(components[3]) == null) {
            throw ApiProblem.notFound();
        }
        return new ApiRoute(components[3]);
    }

    static String requiredSection(String rawQuery) throws ApiProblem {
        if (rawQuery == null) {
            throw ApiProblem.badRequest();
        }

        String section = null;
        boolean found = false;
        String[] parameters = rawQuery.split("&", -1);
        for (String parameter : parameters) {
            int separator = parameter.indexOf('=');
            String rawName = separator < 0 ? parameter : parameter.substring(0, separator);
            String rawValue = separator < 0 ? "" : parameter.substring(separator + 1);
            String name = decodeQueryComponent(rawName);
            String value = decodeQueryComponent(rawValue);
            if (name == null || value == null) {
                throw ApiProblem.badRequest();
            }
            if ("section".equals(name)) {
                if (found) {
                    throw ApiProblem.badRequest();
                }
                found = true;
                section = value;
            }
        }

        if (!found || !SectionNameValidator.isValid(section)) {
            throw ApiProblem.badRequest();
        }
        return section;
    }

    private static String decodeQueryComponent(String component) {
        return UrlPathDecoder.decode(component.replace('+', ' '));
    }

    private static JsonObject parseWriteBody(InputStream requestBody)
            throws IOException, ApiProblem {
        byte[] bytes = readAll(requestBody);
        String body;
        try {
            body = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw ApiProblem.badRequest();
        }

        try {
            JsonReader reader = new JsonReader(new StringReader(body));
            reader.setStrictness(Strictness.STRICT);
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()
                    || reader.peek() != JsonToken.END_DOCUMENT
                    || root.getAsJsonObject().size() == 0) {
                throw ApiProblem.badRequest();
            }

            JsonObject sections = root.getAsJsonObject();
            for (Map.Entry<String, JsonElement> section : sections.entrySet()) {
                if (!SectionNameValidator.isValid(section.getKey())) {
                    throw ApiProblem.badRequest();
                }
            }
            return sections;
        } catch (MalformedJsonException | JsonParseException exception) {
            throw ApiProblem.badRequest();
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void requireJsonContentType(Headers headers) throws ApiProblem {
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null) {
            throw ApiProblem.unsupportedMediaType();
        }
        int parameterStart = contentType.indexOf(';');
        String mediaType = (parameterStart < 0
                ? contentType
                : contentType.substring(0, parameterStart)).trim();
        if (!"application/json".equalsIgnoreCase(mediaType)) {
            throw ApiProblem.unsupportedMediaType();
        }
    }

    private static String requiredMethod(String operation) {
        if (READ.equals(operation) || READ_ALL.equals(operation)) {
            return "GET";
        }
        if (WRITE.equals(operation)) {
            return "POST";
        }
        if (REMOVE.equals(operation) || CLEAR.equals(operation)) {
            return "DELETE";
        }
        return null;
    }

    private static ApiProblem persistenceProblem(
            PersistenceException exception,
            String operation) {
        if (exception.getReason() == PersistenceException.Reason.INVALID_DATA) {
            return ApiProblem.persistenceError("Persistence data is invalid.");
        }
        if (exception.getReason() == PersistenceException.Reason.WRITE_LOCK_TIMEOUT
                || isModifyingOperation(operation)) {
            return ApiProblem.persistenceError("Write failed");
        }
        return ApiProblem.persistenceError("Persistence read failed.");
    }

    private static boolean isModifyingOperation(String operation) {
        return WRITE.equals(operation) || REMOVE.equals(operation) || CLEAR.equals(operation);
    }

    private static ApiResponse errorResponse(ApiProblem problem) {
        JsonObject error = new JsonObject();
        error.addProperty("code", problem.code);
        error.addProperty("message", problem.getMessage());
        JsonObject root = new JsonObject();
        root.add("error", error);
        return new ApiResponse(problem.status, GSON.toJson(root), problem.allowMethod);
    }

    private static void sendResponse(HttpExchange exchange, ApiResponse response)
            throws IOException {
        if (response.allowMethod != null) {
            exchange.getResponseHeaders().set("Allow", response.allowMethod);
        }

        if (response.body == null) {
            exchange.sendResponseHeaders(response.status, -1L);
            exchange.close();
            return;
        }

        byte[] body = response.body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", JSON_CONTENT_TYPE);
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
        exchange.sendResponseHeaders(response.status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void closeRequestBody(HttpExchange exchange) {
        try {
            exchange.getRequestBody().close();
        } catch (IOException ignored) {
            // The request has already been processed or rejected.
        }
    }

    private static final class ApiRoute {
        private final String operation;

        private ApiRoute(String operation) {
            this.operation = operation;
        }
    }

    static final class ApiProblem extends Exception {

        private static final long serialVersionUID = 1L;

        private final int status;
        private final String code;
        private final String allowMethod;

        private ApiProblem(int status, String code, String message, String allowMethod) {
            super(message);
            this.status = status;
            this.code = code;
            this.allowMethod = allowMethod;
        }

        private static ApiProblem badRequest() {
            return new ApiProblem(400, "BAD_REQUEST", "Invalid API request.", null);
        }

        private static ApiProblem notFound() {
            return new ApiProblem(404, "NOT_FOUND", "API resource not found.", null);
        }

        private static ApiProblem sectionNotFound() {
            return new ApiProblem(
                    404,
                    "SECTION_NOT_FOUND",
                    "Section not found.",
                    null);
        }

        private static ApiProblem methodNotAllowed(String allowMethod) {
            return new ApiProblem(
                    405,
                    "METHOD_NOT_ALLOWED",
                    "Method not allowed.",
                    allowMethod);
        }

        private static ApiProblem unsupportedMediaType() {
            return new ApiProblem(
                    415,
                    "UNSUPPORTED_MEDIA_TYPE",
                    "Content type must be application/json.",
                    null);
        }

        private static ApiProblem persistenceError(String message) {
            return new ApiProblem(500, "PERSISTENCE_ERROR", message, null);
        }

        private static ApiProblem internalError() {
            return new ApiProblem(500, "INTERNAL_ERROR", "Internal server error.", null);
        }
    }

    private static final class ApiResponse {

        private final int status;
        private final String body;
        private final String allowMethod;

        private ApiResponse(int status, String body, String allowMethod) {
            this.status = status;
            this.body = body;
            this.allowMethod = allowMethod;
        }

        private static ApiResponse json(int status, JsonElement value) {
            return new ApiResponse(status, GSON.toJson(value), null);
        }

        private static ApiResponse noContent() {
            return new ApiResponse(204, null, null);
        }
    }
}
