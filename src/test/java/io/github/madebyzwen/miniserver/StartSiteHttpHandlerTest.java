package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartSiteHttpHandlerTest {

    @TempDir
    Path temporaryDirectory;

    private Path webRoot;
    private Path sharedConfiguration;
    private Path privateConfiguration;
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        webRoot = temporaryDirectory.resolve("www");
        sharedConfiguration = temporaryDirectory.resolve("config/start-sites.txt");
        privateConfiguration = temporaryDirectory.resolve("profile/MiniServer/Config/start-sites.txt");
        Files.createDirectories(webRoot.resolve("first"));
        Files.write(webRoot.resolve("first/index.html"),
                Collections.singletonList("static application"), StandardCharsets.UTF_8);
        Files.createDirectories(webRoot.resolve("A&B"));
        Files.createDirectories(webRoot.resolve("physical-only"));
        Files.createDirectories(sharedConfiguration.getParent());
        Files.write(sharedConfiguration, Arrays.asList("first", "A&B"), StandardCharsets.UTF_8);
        Files.createDirectories(privateConfiguration.getParent());
        Files.write(privateConfiguration, new byte[0]);

        ConfiguredStartSiteProvider provider = new ConfiguredStartSiteProvider(
                webRoot, sharedConfiguration, privateConfiguration);
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", new RootRequestRouter(
                new LocalStopHandler(UUID.randomUUID().toString()),
                new PersistenceApiHandler(
                        new PersistenceTargetResolver(
                                webRoot, temporaryDirectory.resolve("profile/MiniServer/Data")),
                        new JsonPersistenceStore()),
                new StaticFileHandler(webRoot),
                new WelcomePageHandler(provider),
                new StartSiteSelectionHandler(provider)));
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void rootIsEnglishSelfContainedAndChecksEverySharedSiteWithoutUsingPrivateSelection()
            throws Exception {
        Response response = request("GET", "/", null, null);

        assertEquals(200, response.status);
        assertTrue(response.contentType.startsWith("text/html"));
        assertTrue(response.body.contains("Welcome to Mini Server"));
        assertTrue(response.body.contains("value=\"first\" checked"));
        assertTrue(response.body.contains("value=\"A&amp;B\" checked"));
        assertTrue(response.body.indexOf("value=\"first\"")
                < response.body.indexOf("value=\"A&amp;B\""));
        assertFalse(response.body.contains("physical-only"));
        assertTrue(response.body.contains("%APPDATA%\\MiniServer\\Config\\start-sites.txt"));
        assertFalse(response.body.contains("<link"));
        assertFalse(response.body.contains("<script src="));
    }

    @Test
    void saveStrictlyParsesJsonThenFiltersDeduplicatesAndRestoresSharedOrder()
            throws Exception {
        Response response = request(
                "POST",
                StartSiteSelectionHandler.PATH,
                "application/json; charset=utf-8",
                "{\"sites\":[\"A&B\",\"unknown\",\"first\",\"A&B\","
                        + "\"_shared\",\"../unsafe\",\"https://invalid\"]}");

        assertEquals(204, response.status);
        assertEquals(
                Arrays.asList("first", "A&B"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
    }

    @Test
    void emptySelectionReplacesRatherThanMerges() throws Exception {
        Response response = request(
                "POST", StartSiteSelectionHandler.PATH, "application/json", "{\"sites\":[]}");

        assertEquals(204, response.status);
        assertEquals(0L, Files.size(privateConfiguration));
    }

    @Test
    void malformedShapesWrongMediaTypeAndWrongMethodAreRejectedWithoutChangingSelection()
            throws Exception {
        Files.write(privateConfiguration, Collections.singletonList("first"), StandardCharsets.UTF_8);

        assertEquals(400, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{\"sites\":[],\"extra\":true}").status);
        assertEquals(400, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{}").status);
        assertEquals(400, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{\"sites\":true}").status);
        assertEquals(400, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{\"sites\":[1]}").status);
        assertEquals(400, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{\"sites\":[],\"sites\":[\"first\"]}").status);
        assertEquals(400, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "[]").status);
        assertEquals(415, request("POST", StartSiteSelectionHandler.PATH,
                "text/plain", "{\"sites\":[]}").status);
        assertEquals(400, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{not-json}").status);
        assertEquals(405, request("GET", StartSiteSelectionHandler.PATH, null, null).status);
        assertEquals(405, request("POST", StartSiteSelectionHandler.PATH + "/",
                "application/json", "{\"sites\":[]}").status);
        assertEquals(
                Collections.singletonList("first"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
    }

    @Test
    void unavailableSharedRejectsSavePreservesPrivateAndProducesNoMisleadingChoices()
            throws Exception {
        Files.write(privateConfiguration, Collections.singletonList("first"), StandardCharsets.UTF_8);
        Files.delete(sharedConfiguration);

        assertEquals(503, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{\"sites\":[]}").status);
        assertEquals(
                Collections.singletonList("first"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
        Response root = request("GET", "/", null, null);
        assertTrue(root.body.contains("Shared start-site approval is unavailable"));
        assertFalse(root.body.contains("name=\"site\""));
    }

    @Test
    void internalStopAndApplicationStaticRoutesRemainSeparate() throws Exception {
        assertEquals(403, request("POST", LocalStopHandler.PATH, null, "").status);
        assertEquals(200, request("GET", "/first/", null, null).status);
    }

    @Test
    void everyRootGetRereadsSharedAndReadableEmptySharedStillAllowsEmptySave()
            throws Exception {
        assertTrue(request("GET", "/", null, null).body.contains("value=\"first\""));
        Files.write(sharedConfiguration, new byte[0]);

        Response changed = request("GET", "/", null, null);

        assertFalse(changed.body.contains("name=\"site\""));
        assertTrue(changed.body.contains("No applications are currently approved"));
        assertTrue(changed.body.contains("Save empty selection"));
        assertEquals(204, request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{\"sites\":[]}").status);
    }

    @Test
    void saveRevalidatesSharedChangedSincePageLoadAndAddsNoCorsHeader() throws Exception {
        request("GET", "/", null, null);
        Files.write(sharedConfiguration, Collections.singletonList("first"), StandardCharsets.UTF_8);

        Response saved = request("POST", StartSiteSelectionHandler.PATH,
                "application/json", "{\"sites\":[\"A&B\",\"first\"]}");

        assertEquals(204, saved.status);
        assertEquals("", saved.accessControlAllowOrigin);
        assertEquals(
                Collections.singletonList("first"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
    }

    private Response request(String method, String path, String contentType, String body)
            throws IOException {
        URL url = new URL("http://127.0.0.1:" + server.getAddress().getPort() + path);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        connection.setInstanceFollowRedirects(false);
        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        if (body != null) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String responseBody = stream == null ? "" : read(stream);
        String responseType = connection.getHeaderField("Content-Type");
        String cors = connection.getHeaderField("Access-Control-Allow-Origin");
        connection.disconnect();
        return new Response(
                status,
                responseType == null ? "" : responseType,
                responseBody,
                cors == null ? "" : cors);
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class Response {
        private final int status;
        private final String contentType;
        private final String body;
        private final String accessControlAllowOrigin;

        private Response(
                int status,
                String contentType,
                String body,
                String accessControlAllowOrigin) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.accessControlAllowOrigin = accessControlAllowOrigin;
        }
    }
}
