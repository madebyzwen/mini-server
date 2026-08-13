package io.github.madebyzwen.miniserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceHttpApiTest {

    private static final String JSON_CONTENT_TYPE = "application/json; charset=utf-8";

    @TempDir
    Path temporaryDirectory;

    private Path webRoot;
    private Path privateDataRoot;
    private PersistenceTargetResolver resolver;
    private JsonPersistenceStore store;
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        webRoot = temporaryDirectory.resolve("www");
        privateDataRoot = temporaryDirectory.resolve("profile/MiniServerData");
        Files.createDirectories(webRoot.resolve("example"));
        Files.createDirectories(webRoot.resolve("_shared"));
        resolver = new PersistenceTargetResolver(webRoot, privateDataRoot);
        store = new JsonPersistenceStore(75L, 5L);

        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
                0);
        server.createContext(
                "/",
                new RootRequestRouter(
                        new PersistenceApiHandler(resolver, store),
                        new StaticFileHandler(webRoot)));
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void routesSharedAndPrivateRequestsToTheirOwnPersistenceTargets() throws Exception {
        assertEquals(
                204,
                request(
                        "POST",
                        "/example/api/shared/write",
                        "application/json",
                        "{\"scope\":\"shared\"}").status);
        assertEquals(
                204,
                request(
                        "POST",
                        "/example/api/private/write",
                        "application/json",
                        "{\"scope\":\"private\"}").status);

        assertEquals(
                "{\"scope\":\"shared\"}",
                request("GET", "/example/api/shared/readAll").bodyText());
        assertEquals(
                "{\"scope\":\"private\"}",
                request("GET", "/example/api/private/readAll").bodyText());
        assertTrue(Files.isRegularFile(webRoot.resolve("example/data/data.json")));
        assertTrue(Files.isRegularFile(privateDataRoot.resolve("example/data/data.json")));
    }

    @Test
    void malformedUnknownAndReservedApiRoutesReturnJsonErrorsWithoutCreatingSites()
            throws Exception {
        List<RequestExpectation> expectations = Arrays.asList(
                new RequestExpectation("/unknown/api/shared/readAll", 404, "NOT_FOUND"),
                new RequestExpectation("/_shared/api/shared/readAll", 404, "NOT_FOUND"),
                new RequestExpectation("/example/api/readAll", 400, "BAD_REQUEST"),
                new RequestExpectation("/example/api/default/readAll", 400, "BAD_REQUEST"),
                new RequestExpectation("/example/api/shared/unknown", 404, "NOT_FOUND"),
                new RequestExpectation("/example/shared/api/readAll", 400, "BAD_REQUEST"),
                new RequestExpectation("/example/api/shared/readAll/extra", 400, "BAD_REQUEST"));

        for (RequestExpectation expectation : expectations) {
            assertError(
                    request("GET", expectation.target),
                    expectation.status,
                    expectation.code);
        }

        assertFalse(Files.exists(webRoot.resolve("unknown")));
        assertFalse(Files.exists(privateDataRoot.resolve("unknown")));
    }

    @Test
    void filesystemLookingInputsCannotOverrideTheResolvedPersistenceTarget() throws Exception {
        seed(PersistenceScope.SHARED, objectSection("safe", true));
        String outside = temporaryDirectory.resolve("outside/data.json").toString();

        Response response = request(
                "POST",
                "/example/api/shared/write?target=" + percentEncode(outside),
                "application/json",
                "{\"../outside\":true}");

        assertNoContent(response);
        JsonObject expected = objectSection("safe", true);
        expected.addProperty("../outside", true);
        assertEquals(expected, store.readAll(resolved(PersistenceScope.SHARED, "readAll")));
        assertFalse(Files.exists(temporaryDirectory.resolve("outside")));
    }

    @Test
    void readReturnsObjectsArraysScalarsAndStoredJsonNullDirectly() throws Exception {
        JsonObject object = new JsonObject();
        object.addProperty("theme", "dark");
        JsonArray array = new JsonArray();
        array.add("A");
        array.add("B");
        JsonObject sections = new JsonObject();
        sections.add("object", object);
        sections.add("array", array);
        sections.addProperty("string", "hello");
        sections.addProperty("number", 7);
        sections.addProperty("boolean", true);
        sections.add("optional", JsonNull.INSTANCE);
        seed(PersistenceScope.SHARED, sections);

        assertJsonSuccess("/example/api/shared/read?section=object", object);
        assertJsonSuccess("/example/api/shared/read?section=array", array);
        assertJsonSuccess("/example/api/shared/read?section=string", sections.get("string"));
        assertJsonSuccess("/example/api/shared/read?section=number", sections.get("number"));
        assertJsonSuccess("/example/api/shared/read?section=boolean", sections.get("boolean"));
        assertJsonSuccess("/example/api/shared/read?section=optional", JsonNull.INSTANCE);
    }

    @Test
    void bundledExampleStarterDataIsAvailableThroughTheSharedApi() throws Exception {
        Path bundledData = Paths.get("www", "example", "data", "data.json")
                .toAbsolutePath()
                .normalize();
        Path fixtureData = webRoot.resolve("example/data/data.json");
        Files.createDirectories(fixtureData.getParent());
        Files.copy(bundledData, fixtureData, StandardCopyOption.REPLACE_EXISTING);

        assertJsonSuccess(
                "/example/api/shared/read?section=start",
                JsonParser.parseString("\"Hello Mini Webserver\""));
    }

    @Test
    void templateCanBeInstalledUnderANewSiteWithoutServerChanges() throws Exception {
        Path templateSource = Paths.get("template").toAbsolutePath().normalize();
        Path installedSite = webRoot.resolve("my-app");
        copyDirectory(templateSource, installedSite);
        Files.copy(
                Paths.get("www", "_shared", "mini-api.js").toAbsolutePath().normalize(),
                webRoot.resolve("_shared/mini-api.js"),
                StandardCopyOption.REPLACE_EXISTING);

        Response redirect = request("GET", "/my-app");
        Response page = request("GET", "/my-app/");
        Response library = request("GET", "/_shared/mini-api.js");
        Response blockedData = request("GET", "/my-app/data/data.json");

        assertEquals(301, redirect.status);
        assertEquals("/my-app/", redirect.header("location"));
        assertEquals(200, page.status);
        assertTrue(page.bodyText().contains("Replace this demo with your application."));
        assertEquals(200, library.status);
        assertTrue(library.bodyText().contains("window.MiniApi"));
        assertEquals(404, blockedData.status);
        assertJsonSuccess(
                "/my-app/api/shared/read?section=start",
                JsonParser.parseString("\"Hello Mini Webserver\""));

        assertNoContent(request(
                "POST",
                "/my-app/api/private/write",
                "application/json",
                "{\"profile\":{\"enabled\":true}}"));
        assertTrue(Files.isRegularFile(
                privateDataRoot.resolve("my-app/data/data.json")));
        assertEquals(
                JsonParser.parseString("{\"enabled\":true}"),
                JsonParser.parseString(request(
                        "GET",
                        "/my-app/api/private/read?section=profile").bodyText()));

        JsonObject shared = JsonParser.parseString(request(
                "GET",
                "/my-app/api/shared/readAll").bodyText()).getAsJsonObject();
        assertEquals(1, shared.size());
        assertEquals("Hello Mini Webserver", shared.get("start").getAsString());
    }

    @Test
    void readMissingSectionAndMissingFileReturnSectionNotFound() throws Exception {
        seed(PersistenceScope.SHARED, objectSection("present", true));

        assertError(
                request("GET", "/example/api/shared/read?section=missing"),
                404,
                "SECTION_NOT_FOUND");
        assertError(
                request("GET", "/example/api/private/read?section=missing"),
                404,
                "SECTION_NOT_FOUND");
        assertFalse(Files.exists(privateDataRoot.resolve("example/data/data.json")));
    }

    @Test
    void readAllReturnsCompleteObjectAndEmptyObjectForMissingFile() throws Exception {
        JsonObject sections = new JsonObject();
        sections.addProperty("first", 1);
        sections.addProperty("second", "two");
        seed(PersistenceScope.SHARED, sections);

        Response existing = request("GET", "/example/api/shared/readAll");
        Response missing = request("GET", "/example/api/private/readAll");

        assertEquals(200, existing.status);
        assertEquals(sections, JsonParser.parseString(existing.bodyText()));
        assertEquals(JSON_CONTENT_TYPE, existing.header("content-type"));
        assertEquals(200, missing.status);
        assertEquals("{}", missing.bodyText());
    }

    @Test
    void writeAcceptsJsonMediaTypeParametersAndOneOrManySections() throws Exception {
        Response one = request(
                "POST",
                "/example/api/shared/write",
                "application/json",
                "{\"one\":1}");
        Response many = request(
                "POST",
                "/example/api/shared/write",
                "Application/JSON; charset=utf-8",
                "{\"two\":[2],\"three\":null}\n\t  ");

        assertNoContent(one);
        assertNoContent(many);
        JsonObject expected = new JsonObject();
        expected.addProperty("one", 1);
        JsonArray two = new JsonArray();
        two.add(2);
        expected.add("two", two);
        expected.add("three", JsonNull.INSTANCE);
        assertEquals(expected, store.readAll(resolved(PersistenceScope.SHARED, "readAll")));
    }

    @Test
    void writeRejectsMissingAndWrongContentTypesBeforeParsingBody() throws Exception {
        assertError(
                request("POST", "/example/api/shared/write", null, "{\"value\":1}"),
                415,
                "UNSUPPORTED_MEDIA_TYPE");
        assertError(
                request(
                        "POST",
                        "/example/api/shared/write",
                        "text/plain",
                        "{not json"),
                415,
                "UNSUPPORTED_MEDIA_TYPE");
        assertFalse(Files.exists(webRoot.resolve("example/data/data.json")));
    }

    @Test
    void writeRejectsEveryInvalidBodyWithoutCreatingPersistenceData() throws Exception {
        List<String> invalidBodies = Arrays.asList(
                "{\"broken\":}",
                "{'legacy':true}",
                "{\"valid\":true} garbage",
                "{\"first\":1}\n{\"second\":2}",
                "",
                "[]",
                "\"scalar\"",
                "123",
                "true",
                "null",
                "{}");

        for (String invalidBody : invalidBodies) {
            assertError(
                    request(
                            "POST",
                            "/example/api/shared/write",
                            "application/json",
                            invalidBody),
                    400,
                    "BAD_REQUEST");
        }

        assertFalse(Files.exists(webRoot.resolve("example/data/data.json")));
    }

    @Test
    void writeRejectsInvalidTopLevelSectionNames() throws Exception {
        List<String> invalidNames = Arrays.asList(
                "",
                repeat('a', 129),
                " leading",
                "trailing ",
                "control\u0001name");

        for (String invalidName : invalidNames) {
            JsonObject body = new JsonObject();
            body.addProperty(invalidName, true);
            assertError(
                    request(
                            "POST",
                            "/example/api/shared/write",
                            "application/json",
                            body.toString()),
                    400,
                    "BAD_REQUEST");
        }
    }

    @Test
    void removeDeletesExistingAndJsonNullSectionsAndReportsMissingSections() throws Exception {
        JsonObject sections = new JsonObject();
        sections.addProperty("normal", "value");
        sections.add("nullable", JsonNull.INSTANCE);
        sections.addProperty("preserved", true);
        seed(PersistenceScope.SHARED, sections);

        assertNoContent(request(
                "DELETE",
                "/example/api/shared/remove?section=normal"));
        assertNoContent(request(
                "DELETE",
                "/example/api/shared/remove?section=nullable"));
        assertError(
                request("DELETE", "/example/api/shared/remove?section=missing"),
                404,
                "SECTION_NOT_FOUND");

        JsonObject expected = new JsonObject();
        expected.addProperty("preserved", true);
        assertEquals(expected, store.readAll(resolved(PersistenceScope.SHARED, "readAll")));
    }

    @Test
    void clearSucceedsForPopulatedEmptyAndMissingStores() throws Exception {
        seed(PersistenceScope.SHARED, objectSection("present", true));

        assertNoContent(request("DELETE", "/example/api/shared/clear"));
        assertEquals(
                new JsonObject(),
                store.readAll(resolved(PersistenceScope.SHARED, "readAll")));
        assertNoContent(request("DELETE", "/example/api/shared/clear"));
        assertNoContent(request("DELETE", "/example/api/private/clear"));
        assertFalse(Files.exists(privateDataRoot.resolve("example/data/data.json")));
    }

    @Test
    void sectionValidationAcceptsApprovedLengthsUnicodeAndPunctuation() throws Exception {
        String oneCharacter = "x";
        String maximumLength = repeat('m', 128);
        String unicode = "café";
        String internalSpace = "internal space";
        String punctuation = "hyphen-under_score.period";
        String pathLikeName = "folder/name";
        JsonObject sections = new JsonObject();
        for (String section : Arrays.asList(
                oneCharacter,
                maximumLength,
                unicode,
                internalSpace,
                punctuation,
                pathLikeName)) {
            sections.addProperty(section, section);
        }
        seed(PersistenceScope.SHARED, sections);

        assertEquals(200, request("GET", readPath(oneCharacter)).status);
        assertEquals(200, request("GET", readPath(maximumLength)).status);
        assertEquals(200, request("GET", readPath("caf%C3%A9")).status);
        assertEquals(200, request("GET", readPath("internal+space")).status);
        assertEquals(200, request("GET", readPath(punctuation)).status);
        assertEquals(200, request("GET", readPath("folder%2Fname")).status);
    }

    @Test
    void sectionValidationRejectsMissingEmptyDuplicateOversizedWhitespaceAndControls()
            throws Exception {
        List<String> invalidTargets = Arrays.asList(
                "/example/api/shared/read",
                readPath(""),
                readPath("one&section=two"),
                readPath(repeat('a', 129)),
                readPath("%20leading"),
                readPath("trailing%20"),
                readPath("control%01name"),
                readPath("%C3%28"));

        for (String target : invalidTargets) {
            assertError(request("GET", target), 400, "BAD_REQUEST");
        }

        assertThrows(
                PersistenceApiHandler.ApiProblem.class,
                () -> PersistenceApiHandler.requiredSection("section=%"));
        assertThrows(
                PersistenceApiHandler.ApiProblem.class,
                () -> PersistenceApiHandler.requiredSection("section=%C3%28"));
    }

    @Test
    void everyKnownOperationRejectsUnsupportedMethodWithAllowHeader() throws Exception {
        Map<String, String> operations = new LinkedHashMap<String, String>();
        operations.put("read?section=value", "GET");
        operations.put("readAll", "GET");
        operations.put("write", "POST");
        operations.put("remove?section=value", "DELETE");
        operations.put("clear", "DELETE");

        for (Map.Entry<String, String> operation : operations.entrySet()) {
            Response response = request(
                    "PUT",
                    "/example/api/shared/" + operation.getKey());
            assertError(response, 405, "METHOD_NOT_ALLOWED");
            assertEquals(operation.getValue(), response.header("allow"));
        }
    }

    @Test
    void staticGetAndHeadRemainAvailableAndReservedDataRemainsHidden() throws Exception {
        Path index = webRoot.resolve("example/index.html");
        Files.write(index, "static content".getBytes(StandardCharsets.UTF_8));
        Path dataFile = webRoot.resolve("example/data/data.json");
        Files.createDirectories(dataFile.getParent());
        Files.write(dataFile, "persistence secret".getBytes(StandardCharsets.UTF_8));

        Response get = request("GET", "/example/index.html");
        Response head = request("HEAD", "/example/index.html");
        Response reserved = request("GET", "/example/data/data.json");

        assertEquals(200, get.status);
        assertEquals("static content", get.bodyText());
        assertEquals(200, head.status);
        assertEquals(0, head.body.length);
        assertEquals(404, reserved.status);
        assertFalse(reserved.bodyText().contains("persistence secret"));
    }

    @Test
    void apiNamespaceNeverFallsThroughToStaticServingAndErrorsRemainJson() throws Exception {
        Path apiLookingFile = webRoot.resolve("example/api/shared/readAll");
        Files.createDirectories(apiLookingFile.getParent());
        Files.write(apiLookingFile, "must not be served".getBytes(StandardCharsets.UTF_8));

        Response api = request("GET", "/example/api/shared/readAll");
        Response invalidApi = request("GET", "/example/api/default/readAll");
        Response missingStatic = request("GET", "/example/missing.txt");
        Path nestedApiStatic = webRoot.resolve("example/assets/api");
        Files.createDirectories(nestedApiStatic.getParent());
        Files.write(nestedApiStatic, "normal static api name".getBytes(StandardCharsets.UTF_8));
        Response nestedStatic = request("GET", "/example/assets/api");

        assertEquals(200, api.status);
        assertEquals("{}", api.bodyText());
        assertFalse(api.bodyText().contains("must not be served"));
        assertError(invalidApi, 400, "BAD_REQUEST");
        assertTrue(missingStatic.header("content-type").startsWith("text/plain"));
        assertEquals(200, nestedStatic.status);
        assertEquals("normal static api name", nestedStatic.bodyText());
    }

    @Test
    void invalidExistingPersistenceMapsToSafeServerErrorAndRemainsUnchanged()
            throws Exception {
        Path dataFile = webRoot.resolve("example/data/data.json");
        Files.createDirectories(dataFile.getParent());
        String invalid = "{\"broken\":} " + temporaryDirectory;
        Files.write(dataFile, invalid.getBytes(StandardCharsets.UTF_8));

        Response read = request("GET", "/example/api/shared/readAll");
        Response write = request(
                "POST",
                "/example/api/shared/write",
                "application/json",
                "{\"new\":true}");

        assertPersistenceError(read, "Persistence data is invalid.");
        assertPersistenceError(write, "Persistence data is invalid.");
        assertFalse(read.bodyText().contains(temporaryDirectory.toString()));
        assertFalse(write.bodyText().contains(temporaryDirectory.toString()));
        assertEquals(invalid, readText(dataFile));
    }

    @Test
    void readAndWriteIoFailuresMapToSafeServerErrors() throws Exception {
        Path dataDirectory = webRoot.resolve("example/data");
        Files.createDirectories(dataDirectory.resolve("data.json"));

        Response readFailure = request("GET", "/example/api/shared/readAll");
        assertPersistenceError(readFailure, "Persistence read failed.");
        assertFalse(readFailure.bodyText().contains(temporaryDirectory.toString()));

        Files.delete(dataDirectory.resolve("data.json"));
        Files.delete(dataDirectory);
        Files.write(dataDirectory, new byte[]{1});
        Response writeFailure = request(
                "POST",
                "/example/api/shared/write",
                "application/json",
                "{\"new\":true}");
        assertPersistenceError(writeFailure, "Write failed");
        assertFalse(writeFailure.bodyText().contains(temporaryDirectory.toString()));
    }

    @Test
    void privateTargetResolutionFailuresMapToSafePersistenceErrors() throws Exception {
        PersistenceTargetResolver failingResolver = new PersistenceTargetResolver(
                webRoot,
                new PersistenceTargetResolver.PrivateDataRootProvider() {
                    @Override
                    public Path resolve() throws IOException {
                        throw new IOException("Unavailable private root at " + temporaryDirectory);
                    }
                });
        restartServer(failingResolver, store);

        Response readFailure = request("GET", "/example/api/private/readAll");
        Response writeFailure = request(
                "POST",
                "/example/api/private/write",
                "application/json",
                "{\"value\":true}");

        assertPersistenceError(readFailure, "Persistence read failed.");
        assertPersistenceError(writeFailure, "Write failed");
        assertFalse(readFailure.bodyText().contains(temporaryDirectory.toString()));
        assertFalse(writeFailure.bodyText().contains(temporaryDirectory.toString()));
    }

    @Test
    @Timeout(3)
    void writeLockTimeoutMapsToWriteFailedWithoutChangingData() throws Exception {
        ResolvedPersistenceTarget target = resolved(PersistenceScope.SHARED, "write");
        seed(PersistenceScope.SHARED, objectSection("preserved", "before"));
        String before = readText(target.getDataFile());

        try (FileChannel channel = FileChannel.open(
                store.lockFileFor(target),
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Response response = request(
                    "POST",
                    "/example/api/shared/write",
                    "application/json",
                    "{\"preserved\":\"after\"}");

            assertPersistenceError(response, "Write failed");
            assertEquals(before, readText(target.getDataFile()));
        }
    }

    private void assertJsonSuccess(String target, JsonElement expected) throws Exception {
        Response response = request("GET", target);
        assertEquals(200, response.status);
        assertEquals(expected, JsonParser.parseString(response.bodyText()));
        assertEquals(JSON_CONTENT_TYPE, response.header("content-type"));
    }

    private static void assertNoContent(Response response) {
        assertEquals(204, response.status);
        assertEquals(0, response.body.length);
    }

    private void assertPersistenceError(Response response, String message) {
        assertError(response, 500, "PERSISTENCE_ERROR");
        JsonObject error = JsonParser.parseString(response.bodyText())
                .getAsJsonObject()
                .getAsJsonObject("error");
        assertEquals(message, error.get("message").getAsString());
    }

    private static void assertError(Response response, int status, String code) {
        assertEquals(status, response.status);
        assertEquals(JSON_CONTENT_TYPE, response.header("content-type"));
        JsonObject root = JsonParser.parseString(response.bodyText()).getAsJsonObject();
        assertEquals(1, root.size());
        JsonObject error = root.getAsJsonObject("error");
        assertNotNull(error);
        assertEquals(code, error.get("code").getAsString());
        assertFalse(error.get("message").getAsString().isEmpty());
    }

    private void seed(PersistenceScope scope, JsonObject sections) throws Exception {
        store.write(resolved(scope, "write"), sections);
    }

    private void restartServer(
            PersistenceTargetResolver targetResolver,
            JsonPersistenceStore persistenceStore) throws IOException {
        server.stop(0);
        server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0),
                0);
        server.createContext(
                "/",
                new RootRequestRouter(
                        new PersistenceApiHandler(targetResolver, persistenceStore),
                        new StaticFileHandler(webRoot)));
        server.start();
    }

    private ResolvedPersistenceTarget resolved(PersistenceScope scope, String operation)
            throws Exception {
        return resolver.resolve(
                "/example/api/"
                        + (scope == PersistenceScope.SHARED ? "shared" : "private")
                        + "/"
                        + operation).get();
    }

    private Response request(String method, String target) throws IOException {
        return request(method, target, null, "");
    }

    private Response request(
            String method,
            String target,
            String contentType,
            String body) throws IOException {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress("127.0.0.1", server.getAddress().getPort()),
                    1000);
            socket.setSoTimeout(3000);
            OutputStream output = socket.getOutputStream();
            StringBuilder request = new StringBuilder();
            request.append(method).append(' ').append(target).append(" HTTP/1.1\r\n")
                    .append("Host: 127.0.0.1\r\n")
                    .append("Connection: close\r\n")
                    .append("Content-Length: ").append(bodyBytes.length).append("\r\n");
            if (contentType != null) {
                request.append("Content-Type: ").append(contentType).append("\r\n");
            }
            request.append("\r\n");
            output.write(request.toString().getBytes(StandardCharsets.ISO_8859_1));
            output.write(bodyBytes);
            output.flush();
            return Response.parse(readAll(socket.getInputStream()));
        }
    }

    private static String readPath(String encodedSection) {
        return "/example/api/shared/read?section=" + encodedSection;
    }

    private static JsonObject objectSection(String name, boolean value) {
        JsonObject object = new JsonObject();
        object.addProperty(name, value);
        return object;
    }

    private static JsonObject objectSection(String name, String value) {
        JsonObject object = new JsonObject();
        object.addProperty(name, value);
        return object;
    }

    private static String repeat(char character, int count) {
        char[] characters = new char[count];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private static String percentEncode(String value) {
        StringBuilder encoded = new StringBuilder();
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        for (byte valueByte : bytes) {
            int unsigned = valueByte & 0xff;
            if (unsigned >= 'a' && unsigned <= 'z'
                    || unsigned >= 'A' && unsigned <= 'Z'
                    || unsigned >= '0' && unsigned <= '9'
                    || unsigned == '-'
                    || unsigned == '_'
                    || unsigned == '.') {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%');
                String hex = Integer.toHexString(unsigned).toUpperCase(Locale.ROOT);
                if (hex.length() == 1) {
                    encoded.append('0');
                }
                encoded.append(hex);
            }
        }
        return encoded.toString();
    }

    private static String readText(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                    throws IOException {
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)
                    throws IOException {
                Files.copy(
                        file,
                        target.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
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

    private static final class RequestExpectation {
        private final String target;
        private final int status;
        private final String code;

        private RequestExpectation(String target, int status, String code) {
            this.target = target;
            this.status = status;
            this.code = code;
        }
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
                throw new AssertionError("HTTP response did not contain complete headers.");
            }

            String headerText = new String(
                    rawResponse,
                    0,
                    headerEnd,
                    StandardCharsets.ISO_8859_1);
            String[] lines = headerText.split("\r\n");
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
