package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticFileHandlerTest {

    @TempDir
    Path temporaryDirectory;

    private Path webRoot;
    private HttpServer server;

    @BeforeEach
    void createWebRoot() throws IOException {
        webRoot = temporaryDirectory.resolve("www");
        Files.createDirectories(webRoot);
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void servesHtmlFileAndDirectoryIndex() throws Exception {
        writeText("example/index.html", "<h1>Hello</h1>");
        startServer(new StaticFileHandler(webRoot));

        Response fileResponse = request("GET", "/example/index.html");
        Response directoryResponse = request("GET", "/example/");

        assertEquals(200, fileResponse.status);
        assertEquals("<h1>Hello</h1>", fileResponse.bodyText());
        assertTrue(fileResponse.header("content-type").startsWith("text/html"));
        assertEquals(200, directoryResponse.status);
        assertEquals("<h1>Hello</h1>", directoryResponse.bodyText());
    }

    @Test
    void servesCssAndJavaScriptWithTextualContentTypes() throws Exception {
        writeText("example/assets/site.css", "body { color: navy; }");
        writeText("example/assets/app.js", "window.ready = true;");
        startServer(new StaticFileHandler(webRoot));

        Response css = request("GET", "/example/assets/site.css");
        Response javascript = request("GET", "/example/assets/app.js");

        assertEquals(200, css.status);
        assertTrue(css.header("content-type").startsWith("text/css"));
        assertEquals(200, javascript.status);
        assertTrue(javascript.header("content-type").startsWith("application/javascript"));
    }

    @Test
    void servesSharedMiniApiLibraryAsNormalJavaScript() throws Exception {
        writeText("_shared/mini-api.js", "window.MiniApi = {};");
        startServer(new StaticFileHandler(webRoot));

        Response response = request("GET", "/_shared/mini-api.js");

        assertEquals(200, response.status);
        assertEquals("window.MiniApi = {};", response.bodyText());
        assertTrue(response.header("content-type").startsWith("application/javascript"));
    }

    @Test
    void servesStaticJsonOutsideReservedDirectory() throws Exception {
        writeText("example/assets/config.json", "{\"theme\":\"blue\"}");
        startServer(new StaticFileHandler(webRoot));

        Response response = request("GET", "/example/assets/config.json?ignored=data.json");

        assertEquals(200, response.status);
        assertEquals("{\"theme\":\"blue\"}", response.bodyText());
        assertTrue(response.header("content-type").startsWith("application/json"));
    }

    @Test
    void plusCharacterRemainsLiteralInUrlPath() throws Exception {
        writeText("example/assets/one+two.txt", "literal plus");
        startServer(new StaticFileHandler(webRoot));

        Response response = request("GET", "/example/assets/one+two.txt");

        assertEquals(200, response.status);
        assertEquals("literal plus", response.bodyText());
    }

    @Test
    void preservesImageBytesAndUsesImageContentType() throws Exception {
        byte[] imageBytes = new byte[]{0, 1, 2, 3, (byte) 0xff};
        writeBytes("example/assets/pixel.png", imageBytes);
        startServer(new StaticFileHandler(webRoot));

        Response response = request("GET", "/example/assets/pixel.png");

        assertEquals(200, response.status);
        assertEquals("image/png", response.header("content-type"));
        assertArrayEquals(imageBytes, response.body);
    }

    @Test
    void unknownExtensionUsesGenericBinaryContentType() throws Exception {
        byte[] content = new byte[]{9, 8, 7};
        writeBytes("example/assets/archive.custom", content);
        startServer(new StaticFileHandler(webRoot));

        Response response = request("GET", "/example/assets/archive.custom");

        assertEquals(200, response.status);
        assertEquals("application/octet-stream", response.header("content-type"));
        assertArrayEquals(content, response.body);
    }

    @Test
    void missingFileAndDirectoryWithoutIndexReturnNotFoundWithoutListing() throws Exception {
        Files.createDirectories(webRoot.resolve("example/empty"));
        writeText("example/empty/private.txt", "not a directory listing");
        startServer(new StaticFileHandler(webRoot));

        Response missing = request("GET", "/example/missing.txt");
        Response directory = request("GET", "/example/empty/");

        assertEquals(404, missing.status);
        assertFalse(missing.bodyText().contains(temporaryDirectory.toString()));
        assertEquals(404, directory.status);
        assertFalse(directory.bodyText().contains("private.txt"));
    }

    @Test
    void reservedApplicationDataDirectoryIsNeverServed() throws Exception {
        String secret = "reserved-persistence-secret";
        writeText("example/data/data.json", secret);
        writeText("example/assets/config.json", "{\"public\":true}");
        startServer(new StaticFileHandler(webRoot));

        List<String> reservedTargets = Arrays.asList(
                "/example/data/data.json",
                "/example/data/",
                "/example/DATA/data.json");
        for (String target : reservedTargets) {
            Response response = request("GET", target);
            assertEquals(404, response.status);
            assertFalse(response.bodyText().contains(secret));
        }

        assertEquals(200, request("GET", "/example/assets/config.json").status);
    }

    @Test
    void traversalFormsCannotExposeFilesOutsideWebRoot() throws Exception {
        String secret = "outside-traversal-secret";
        Files.write(
                temporaryDirectory.resolve("secret.txt"),
                secret.getBytes(StandardCharsets.UTF_8));
        startServer(new StaticFileHandler(webRoot));

        List<String> traversalTargets = Arrays.asList(
                "/../secret.txt",
                "/%2e%2e/secret.txt",
                "/example/../../secret.txt",
                "/example/%2e%2e/%2e%2e/secret.txt",
                "/example/..\\..\\secret.txt",
                "/example/%2e%2e%5c%2e%2e%5csecret.txt");

        for (String target : traversalTargets) {
            Response response = request("GET", target);
            assertNotEquals(200, response.status, target);
            assertFalse(response.bodyText().contains(secret), target);
        }
    }

    @Test
    void absoluteAndMalformedPathAttemptsFailSafely() throws Exception {
        startServer(new StaticFileHandler(webRoot));

        List<String> manipulatedTargets = Arrays.asList(
                "//etc/passwd",
                "/%2Fetc/passwd",
                "/C%3A/Windows/win.ini",
                "/example/%",
                "/example/%2G/file.txt");

        for (String target : manipulatedTargets) {
            assertNotEquals(200, request("GET", target).status, target);
        }
    }

    @Test
    void unsupportedMethodReturnsMethodNotAllowedWithoutChangingFile() throws Exception {
        Path file = writeText("example/index.html", "unchanged");
        startServer(new StaticFileHandler(webRoot));

        Response response = request("POST", "/example/index.html");

        assertEquals(405, response.status);
        assertEquals("GET, HEAD", response.header("allow"));
        assertEquals("unchanged", new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test
    void servesMultipleApplicationsAndSharedStaticResourcesGenerically() throws Exception {
        writeText("first/index.html", "first application");
        writeText("second/index.html", "second application");
        writeText("_shared/library.js", "window.shared = true;");
        startServer(new StaticFileHandler(webRoot));

        assertEquals("first application", request("GET", "/first/").bodyText());
        assertEquals("second application", request("GET", "/second/").bodyText());
        Response shared = request("GET", "/_shared/library.js");
        assertEquals(200, shared.status);
        assertEquals("window.shared = true;", shared.bodyText());
    }

    @Test
    void headReturnsGetHeadersWithoutBody() throws Exception {
        byte[] content = "head content".getBytes(StandardCharsets.UTF_8);
        writeBytes("example/file.txt", content);
        startServer(new StaticFileHandler(webRoot));

        Response response = request("HEAD", "/example/file.txt");

        assertEquals(200, response.status);
        assertEquals(Integer.toString(content.length), response.header("content-length"));
        assertTrue(response.header("content-type").startsWith("text/plain"));
        assertEquals(0, response.body.length);
    }

    @Test
    void readFailureReturnsInternalErrorWithoutExposingPath() throws Exception {
        writeText("example/file.txt", "unreadable content");
        StaticFileHandler.FileOpener failingOpener = file -> {
            throw new IOException("deliberate read failure at " + file);
        };
        startServer(new StaticFileHandler(webRoot, failingOpener));

        Response response = request("GET", "/example/file.txt");

        assertEquals(500, response.status);
        assertEquals("Internal Server Error", response.bodyText());
        assertFalse(response.bodyText().contains(temporaryDirectory.toString()));
    }

    @Test
    void symbolicLinkCannotExposeFileOutsideWebRootWhenSupported() throws Exception {
        Path outsideFile = temporaryDirectory.resolve("outside.txt");
        Files.write(outsideFile, "symlink secret".getBytes(StandardCharsets.UTF_8));
        Path link = webRoot.resolve("example/assets/outside.txt");
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, outsideFile);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Symbolic links are unavailable in this test environment.");
        }
        startServer(new StaticFileHandler(webRoot));

        Response response = request("GET", "/example/assets/outside.txt");

        assertEquals(404, response.status);
        assertFalse(response.bodyText().contains("symlink secret"));
    }

    private void startServer(StaticFileHandler handler) throws IOException {
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
                0);
        server.createContext("/", handler);
        server.start();
    }

    private Response request(String method, String target) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", server.getAddress().getPort()), 1000);
            socket.setSoTimeout(2000);
            OutputStream output = socket.getOutputStream();
            String request = method + " " + target + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1\r\n"
                    + "Connection: close\r\n"
                    + "Content-Length: 0\r\n\r\n";
            output.write(request.getBytes(StandardCharsets.ISO_8859_1));
            output.flush();
            return Response.parse(readAll(socket.getInputStream()));
        }
    }

    private Path writeText(String relativePath, String content) throws IOException {
        return writeBytes(relativePath, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path writeBytes(String relativePath, byte[] content) throws IOException {
        Path file = webRoot.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return file;
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class Response {

        private final int status;
        private final Map<String, String> headers;
        private final byte[] body;

        private Response(int status, Map<String, String> headers, byte[] body) {
            this.status = status;
            this.headers = headers;
            this.body = body;
        }

        private static Response parse(byte[] rawResponse) {
            int headerEnd = findHeaderEnd(rawResponse);
            if (headerEnd < 0) {
                throw new AssertionError("HTTP response did not contain a complete header block.");
            }

            String headerText = new String(rawResponse, 0, headerEnd, StandardCharsets.ISO_8859_1);
            String[] lines = headerText.split("\\r\\n");
            String[] statusParts = lines[0].split(" ", 3);
            Map<String, String> headers = new LinkedHashMap<String, String>();
            for (int index = 1; index < lines.length; index++) {
                int separator = lines[index].indexOf(':');
                if (separator > 0) {
                    headers.put(
                            lines[index].substring(0, separator).toLowerCase(Locale.ROOT),
                            lines[index].substring(separator + 1).trim());
                }
            }
            return new Response(
                    Integer.parseInt(statusParts[1]),
                    headers,
                    Arrays.copyOfRange(rawResponse, headerEnd + 4, rawResponse.length));
        }

        private String header(String name) {
            return headers.get(name.toLowerCase(Locale.ROOT));
        }

        private String bodyText() {
            return new String(body, StandardCharsets.UTF_8);
        }

        private static int findHeaderEnd(byte[] response) {
            for (int index = 0; index <= response.length - 4; index++) {
                if (response[index] == '\r'
                        && response[index + 1] == '\n'
                        && response[index + 2] == '\r'
                        && response[index + 3] == '\n') {
                    return index;
                }
            }
            return -1;
        }
    }
}
