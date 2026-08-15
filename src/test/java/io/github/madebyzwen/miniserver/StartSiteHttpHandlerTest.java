package io.github.madebyzwen.miniserver;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private RecordingBrowserLauncher browserLauncher;
    private ByteArrayOutputStream errorBytes;

    @BeforeEach
    void startServer() throws Exception {
        webRoot = temporaryDirectory.resolve("www");
        sharedConfiguration = temporaryDirectory.resolve("config/start-sites.txt");
        privateConfiguration = temporaryDirectory.resolve(
                "profile/MiniServer/Config/start-sites.txt");
        Files.createDirectories(webRoot.resolve("first"));
        Files.write(webRoot.resolve("first/index.html"),
                Collections.singletonList("static application"), StandardCharsets.UTF_8);
        Files.createDirectories(webRoot.resolve("A&B"));
        Files.createDirectories(webRoot.resolve("physical-only"));
        write(sharedConfiguration, "first", "A&B");
        startWith(new ConfiguredStartSiteProvider(
                webRoot, sharedConfiguration, privateConfiguration));
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void missingPrivateRootUsesUnsavedAllSelectedProposalAndCompleteClientGuard()
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
        assertTrue(response.body.contains("Nothing is saved until Save and open succeeds"));
        assertTrue(response.body.contains("Save replaces your complete personal selection"));
        assertTrue(response.body.contains("uses it for future normal starts"));
        assertTrue(response.body.contains(">Save and open</button>"));
        assertTrue(response.body.contains("let saving=false"));
        assertTrue(response.body.contains("if(saving)return"));
        assertTrue(response.body.contains("addEventListener('change',update)"));
        assertTrue(response.body.contains("catch(error){saving=false"));
        assertTrue(response.body.contains("window.location.replace(result.targets[0])"));
        assertFalse(response.body.contains("window.open"));
        assertFalse(response.body.contains("window.close"));
        assertFalse(response.body.contains("<link"));
        assertFalse(response.body.contains("<script src="));
        assertFalse(Files.exists(privateConfiguration));

        request("GET", "/", null, null);
        assertFalse(Files.exists(privateConfiguration));
    }

    @Test
    void readablePrivateRootChecksOnlyCurrentIntersectionAndNotNewSharedSites()
            throws Exception {
        write(privateConfiguration, "A&B", "removed");

        Response response = request("GET", "/", null, null);

        assertTrue(response.body.contains("value=\"first\""));
        assertFalse(response.body.contains("value=\"first\" checked"));
        assertTrue(response.body.contains("value=\"A&amp;B\" checked"));
        assertFalse(response.body.contains("removed"));
    }

    @Test
    void unreadablePrivateRootWarnsAndDoesNotGuessSelections() throws Exception {
        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            if (file.equals(privateConfiguration)) {
                throw new IOException("deliberately unreadable Private file");
            }
            return Optional.of(Files.readAllLines(file, StandardCharsets.UTF_8));
        };
        restartWith(new ConfiguredStartSiteProvider(
                webRoot, sharedConfiguration, privateConfiguration, reader));

        Response response = request("GET", "/", null, null);

        assertTrue(response.body.contains("could not be read"));
        assertTrue(response.body.contains("No saved selection has been guessed"));
        assertFalse(response.body.contains(" checked"));
        assertTrue(response.body.contains("id=\"save\" type=\"submit\" disabled"));
    }

    @Test
    void emptyOrUnavailableSharedHasNoSaveAction() throws Exception {
        write(sharedConfiguration);
        Response empty = request("GET", "/", null, null);
        assertTrue(empty.body.contains("no applications available to select or save"));
        assertFalse(empty.body.contains("id=\"save\""));

        Files.delete(sharedConfiguration);
        Response unavailable = request("GET", "/", null, null);
        assertTrue(unavailable.body.contains("Shared start-site approval cannot currently be read"));
        assertFalse(unavailable.body.contains("id=\"save\""));
    }

    @Test
    void successfulSaveReturnsCanonicalSitesAndServerGeneratedActualPortTargets()
            throws Exception {
        Response response = request(
                "POST",
                StartSiteSelectionHandler.PATH,
                "application/json; charset=utf-8",
                "{\"sites\":[\"A&B\",\"unknown\",\"first\",\"A&B\","
                        + "\"_shared\",\"../unsafe\",\"https://invalid\"]}");

        assertEquals(200, response.status);
        assertEquals("application/json; charset=utf-8", response.contentType);
        JsonObject json = JsonParser.parseString(response.body).getAsJsonObject();
        assertEquals("[\"first\",\"A&B\"]", json.getAsJsonArray("sites").toString());
        int port = server.getAddress().getPort();
        assertEquals(
                "[\"http://127.0.0.1:" + port
                        + "/first/\",\"http://127.0.0.1:" + port + "/A&B/\"]",
                json.getAsJsonArray("targets").toString());
        assertFalse(response.body.contains("unknown"));
        assertFalse(response.body.contains("unsafe"));
        assertEquals(Collections.singletonList(
                "http://127.0.0.1:" + port + "/A&B/"), browserLauncher.attempted);
        assertEquals(Arrays.asList("first", "A&B"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
        assertEquals("", response.accessControlAllowOrigin);
    }

    @Test
    void malformedEmptyConflictAndUnavailableSavesPreservePrivate() throws Exception {
        write(privateConfiguration, "first");

        assertEquals(400, post("{\"sites\":[]}").status);
        assertPrivate("first");
        assertEquals(400, post("{\"sites\":[],\"extra\":true}").status);
        assertEquals(400, post("{}").status);
        assertEquals(400, post("{\"sites\":true}").status);
        assertEquals(400, post("{\"sites\":[1]}").status);
        assertEquals(400, post("{\"sites\":[],\"sites\":[\"first\"]}").status);
        assertEquals(400, post("[]").status);
        assertEquals(400, post("{not-json}").status);
        assertPrivate("first");

        assertEquals(409, post("{\"sites\":[\"unknown\",\"../unsafe\"]}").status);
        assertPrivate("first");

        write(sharedConfiguration);
        assertEquals(409, post("{\"sites\":[\"first\"]}").status);
        assertPrivate("first");

        Files.delete(sharedConfiguration);
        assertEquals(503, post("{\"sites\":[\"first\"]}").status);
        assertPrivate("first");
        assertTrue(browserLauncher.attempted.isEmpty());
    }

    @Test
    void methodMediaTypeAndRouteValidationPreservePrivate() throws Exception {
        write(privateConfiguration, "first");

        assertEquals(415, request("POST", StartSiteSelectionHandler.PATH,
                "text/plain", "{\"sites\":[\"first\"]}").status);
        assertEquals(405, request("GET", StartSiteSelectionHandler.PATH, null, null).status);
        assertEquals(405, request("POST", StartSiteSelectionHandler.PATH + "/",
                "application/json", "{\"sites\":[\"first\"]}").status);
        assertPrivate("first");
    }

    @Test
    void additionalLaunchFailureIsIsolatedAndLaterTargetsAreAttempted() throws Exception {
        Files.createDirectories(webRoot.resolve("third"));
        write(sharedConfiguration, "first", "A&B", "third");
        int port = server.getAddress().getPort();
        browserLauncher.failUrls.add("http://127.0.0.1:" + port + "/A&B/");

        Response response = post("{\"sites\":[\"third\",\"first\",\"A&B\"]}");

        assertEquals(200, response.status);
        assertEquals(Arrays.asList(
                "http://127.0.0.1:" + port + "/A&B/",
                "http://127.0.0.1:" + port + "/third/"), browserLauncher.attempted);
        assertFalse(browserLauncher.attempted.contains(
                "http://127.0.0.1:" + port + "/first/"));
        assertEquals(Arrays.asList("first", "A&B", "third"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
        assertTrue(new String(errorBytes.toByteArray(), StandardCharsets.UTF_8)
                .contains("A&B"));
    }

    @Test
    void internalStopAndApplicationStaticRoutesRemainSeparate() throws Exception {
        assertEquals(403, request("POST", LocalStopHandler.PATH, null, "").status);
        assertEquals(200, request("GET", "/first/", null, null).status);
    }

    private void restartWith(ConfiguredStartSiteProvider provider) throws IOException {
        server.stop(0);
        startWith(provider);
    }

    private void startWith(ConfiguredStartSiteProvider provider) throws IOException {
        browserLauncher = new RecordingBrowserLauncher();
        errorBytes = new ByteArrayOutputStream();
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
                new StartSiteSelectionHandler(
                        provider,
                        browserLauncher,
                        new PrintStream(errorBytes, true, "UTF-8"))));
        server.start();
    }

    private Response post(String body) throws IOException {
        return request("POST", StartSiteSelectionHandler.PATH, "application/json", body);
    }

    private void assertPrivate(String... lines) throws IOException {
        assertEquals(Arrays.asList(lines),
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
        return new Response(status, responseType == null ? "" : responseType,
                responseBody, cors == null ? "" : cors);
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

    private static void write(Path file, String... lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
    }

    private static final class RecordingBrowserLauncher implements BrowserLauncher {
        private final List<String> attempted = new ArrayList<String>();
        private final Set<String> failUrls = new HashSet<String>();

        @Override
        public void open(String url) throws IOException {
            attempted.add(url);
            if (failUrls.contains(url)) {
                throw new IOException("deliberate browser failure");
            }
        }
    }

    private static final class Response {
        private final int status;
        private final String contentType;
        private final String body;
        private final String accessControlAllowOrigin;

        private Response(int status, String contentType, String body,
                String accessControlAllowOrigin) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.accessControlAllowOrigin = accessControlAllowOrigin;
        }
    }
}
