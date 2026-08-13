package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class StaticFileHandler implements HttpHandler {

    private static final byte[] NOT_FOUND_BODY = "Not Found".getBytes(StandardCharsets.UTF_8);
    private static final byte[] METHOD_NOT_ALLOWED_BODY =
            "Method Not Allowed".getBytes(StandardCharsets.UTF_8);
    private static final byte[] INTERNAL_ERROR_BODY =
            "Internal Server Error".getBytes(StandardCharsets.UTF_8);
    private static final int PERMANENT_REDIRECT = 301;
    private static final int COPY_BUFFER_SIZE = 8192;

    private static final FileOpener DEFAULT_FILE_OPENER = new FileOpener() {
        @Override
        public InputStream open(Path file) throws IOException {
            return Files.newInputStream(file);
        }
    };

    private final Path webRoot;
    private final FileOpener fileOpener;

    StaticFileHandler(Path webRoot) throws IOException {
        this(webRoot, DEFAULT_FILE_OPENER);
    }

    StaticFileHandler(Path webRoot, FileOpener fileOpener) throws IOException {
        if (!Files.isDirectory(webRoot) || !Files.isReadable(webRoot)) {
            throw new IOException("The Mini Server web root is not an accessible directory.");
        }
        this.webRoot = webRoot.toRealPath();
        this.fileOpener = fileOpener;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        boolean headRequest = "HEAD".equals(method);
        if (!"GET".equals(method) && !headRequest) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            sendResponse(exchange, 405, METHOD_NOT_ALLOWED_BODY, false);
            return;
        }

        boolean responseStarted = false;
        try {
            ResolvedResource resolved = resolveResource(exchange.getRequestURI().getRawPath());
            if (resolved == null) {
                sendResponse(exchange, 404, NOT_FOUND_BODY, headRequest);
                return;
            }
            Path resource = resolved.file;
            if (!Files.isReadable(resource)) {
                throw new AccessDeniedException("Static resource is not readable.");
            }
            if (resolved.directoryRedirect) {
                sendDirectoryRedirect(exchange);
                return;
            }

            long contentLength = Files.size(resource);
            try (InputStream input = fileOpener.open(resource)) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", contentType(resource));
                headers.set("Content-Length", Long.toString(contentLength));

                if (headRequest || contentLength == 0L) {
                    exchange.sendResponseHeaders(200, -1L);
                    responseStarted = true;
                    exchange.close();
                    return;
                }

                exchange.sendResponseHeaders(200, contentLength);
                responseStarted = true;
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    copy(input, responseBody);
                }
            }
        } catch (IOException exception) {
            if (!responseStarted) {
                sendResponse(exchange, 500, INTERNAL_ERROR_BODY, headRequest);
            } else {
                exchange.close();
            }
        } catch (RuntimeException exception) {
            if (!responseStarted) {
                sendResponse(exchange, 500, INTERNAL_ERROR_BODY, headRequest);
            } else {
                exchange.close();
            }
        }
    }

    private ResolvedResource resolveResource(String rawPath) throws IOException {
        String decodedPath = UrlPathDecoder.decode(rawPath);
        if (decodedPath == null
                || !decodedPath.startsWith("/")
                || decodedPath.startsWith("//")
                || decodedPath.indexOf('\\') >= 0
                || decodedPath.indexOf(':') >= 0
                || UrlPathDecoder.containsControlCharacter(decodedPath)) {
            return null;
        }

        List<String> segments = normalizedSegments(decodedPath);
        if (segments == null || isReservedPersistencePath(segments)) {
            return null;
        }

        Path candidate = webRoot;
        for (String segment : segments) {
            candidate = candidate.resolve(segment);
        }
        candidate = candidate.normalize();
        if (!candidate.startsWith(webRoot)
                || !existsWithoutFollowingLinks(candidate)) {
            return null;
        }

        Path realResource;
        try {
            realResource = candidate.toRealPath();
        } catch (NoSuchFileException exception) {
            return null;
        }
        if (!isContained(realResource) || isReservedPersistencePath(realResource)) {
            return null;
        }

        BasicFileAttributes attributes =
                Files.readAttributes(realResource, BasicFileAttributes.class);
        boolean directory = attributes.isDirectory();
        if (directory) {
            Path indexFile = realResource.resolve("index.html");
            if (!existsWithoutFollowingLinks(indexFile)) {
                return null;
            }
            try {
                realResource = indexFile.toRealPath();
            } catch (NoSuchFileException exception) {
                return null;
            }
            if (!isContained(realResource) || isReservedPersistencePath(realResource)) {
                return null;
            }
            attributes = Files.readAttributes(realResource, BasicFileAttributes.class);
        }

        return attributes.isRegularFile()
                ? new ResolvedResource(realResource, directory && !rawPath.endsWith("/"))
                : null;
    }

    private static void sendDirectoryRedirect(HttpExchange exchange) throws IOException {
        String rawPath = exchange.getRequestURI().getRawPath();
        String rawQuery = exchange.getRequestURI().getRawQuery();
        String location = rawPath + "/";
        if (rawQuery != null) {
            location += "?" + rawQuery;
        }

        exchange.getResponseHeaders().set("Location", location);
        exchange.getResponseHeaders().set("Content-Length", "0");
        exchange.sendResponseHeaders(PERMANENT_REDIRECT, -1L);
        exchange.close();
    }

    private static boolean existsWithoutFollowingLinks(Path path) throws IOException {
        try {
            Files.readAttributes(
                    path,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException exception) {
            return false;
        }
    }

    private boolean isContained(Path resource) {
        return resource.startsWith(webRoot);
    }

    private boolean isReservedPersistencePath(Path resource) {
        return isReservedPersistencePath(pathSegments(webRoot.relativize(resource)));
    }

    private static boolean isReservedPersistencePath(List<String> segments) {
        return segments.size() >= 2
                && !"_shared".equalsIgnoreCase(segments.get(0))
                && "data".equalsIgnoreCase(segments.get(1));
    }

    private static List<String> normalizedSegments(String decodedPath) {
        String[] rawSegments = decodedPath.substring(1).split("/", -1);
        List<String> segments = new ArrayList<String>();
        for (String segment : rawSegments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (".".equals(segment) || "..".equals(segment)) {
                return null;
            }
            segments.add(segment);
        }
        return segments;
    }

    private static List<String> pathSegments(Path path) {
        List<String> segments = new ArrayList<String>();
        for (Path segment : path) {
            segments.add(segment.toString());
        }
        return segments;
    }

    private static String contentType(Path resource) {
        String fileName = resource.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html; charset=utf-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (fileName.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (fileName.endsWith(".json")) {
            return "application/json; charset=utf-8";
        }
        if (fileName.endsWith(".txt")) {
            return "text/plain; charset=utf-8";
        }
        if (fileName.endsWith(".png")) {
            return "image/png";
        }
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (fileName.endsWith(".gif")) {
            return "image/gif";
        }
        if (fileName.endsWith(".svg")) {
            return "image/svg+xml; charset=utf-8";
        }
        if (fileName.endsWith(".ico")) {
            return "image/x-icon";
        }
        if (fileName.endsWith(".webp")) {
            return "image/webp";
        }
        if (fileName.endsWith(".woff")) {
            return "font/woff";
        }
        if (fileName.endsWith(".woff2")) {
            return "font/woff2";
        }
        return "application/octet-stream";
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
    }

    private static void sendResponse(
            HttpExchange exchange,
            int status,
            byte[] body,
            boolean headRequest) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
        if (headRequest || body.length == 0) {
            exchange.sendResponseHeaders(status, -1L);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(status, body.length);
        try (InputStream input = new ByteArrayInputStream(body);
             OutputStream responseBody = exchange.getResponseBody()) {
            copy(input, responseBody);
        }
    }

    interface FileOpener {
        InputStream open(Path file) throws IOException;
    }

    private static final class ResolvedResource {

        private final Path file;
        private final boolean directoryRedirect;

        private ResolvedResource(Path file, boolean directoryRedirect) {
            this.file = file;
            this.directoryRedirect = directoryRedirect;
        }
    }
}
