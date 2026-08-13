package io.github.madebyzwen.miniserver;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPersistenceStoreTest {

    @TempDir
    Path temporaryDirectory;

    private Path webRoot;
    private Path privateDataRoot;
    private JsonPersistenceStore store;

    @BeforeEach
    void createPersistenceContext() throws IOException {
        webRoot = temporaryDirectory.resolve("www");
        privateDataRoot = temporaryDirectory.resolve("profile/MiniServerData");
        Files.createDirectories(webRoot.resolve("example"));
        store = new JsonPersistenceStore();
    }

    @Test
    void missingFileReadsAsEmptyWithoutCreatingArtifacts() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "readAll");

        assertEquals(new JsonObject(), store.readAll(target));
        assertThrows(SectionNotFoundException.class, () -> store.read(target, "missing"));
        assertFalse(Files.exists(target.getDataFile().getParent()));
        assertFalse(Files.exists(target.getDataFile()));
    }

    @Test
    void storedJsonNullIsDistinctFromAMissingSection() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        JsonObject sections = new JsonObject();
        sections.add("optional", JsonNull.INSTANCE);

        store.write(target, sections);

        assertTrue(store.read(target, "optional").isJsonNull());
        assertThrows(SectionNotFoundException.class, () -> store.read(target, "missing"));
    }

    @Test
    void writeCreatesSharedAndPrivatePersistenceDirectoriesForAValidatedSite()
            throws Exception {
        ResolvedPersistenceTarget shared = target(PersistenceScope.SHARED, "write");
        ResolvedPersistenceTarget privateTarget = target(PersistenceScope.PRIVATE, "write");

        store.write(shared, section("shared", "value"));
        store.write(privateTarget, section("private", "value"));

        assertTrue(Files.isRegularFile(shared.getDataFile()));
        assertTrue(Files.isRegularFile(privateTarget.getDataFile()));
        assertEquals(
                webRoot.resolve("example/data/data.json").toRealPath(),
                shared.getDataFile().toRealPath());
        assertEquals(
                privateDataRoot.resolve("example/data/data.json").toRealPath(),
                privateTarget.getDataFile().toRealPath());
    }

    @Test
    void writesMultipleSectionsAndPreservesAllJsonValueTypes() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        JsonObject object = new JsonObject();
        object.addProperty("theme", "dark");
        JsonArray array = new JsonArray();
        array.add("A");
        array.add(2);
        JsonObject sections = new JsonObject();
        sections.add("object", object);
        sections.add("array", array);
        sections.addProperty("string", "text");
        sections.addProperty("number", 123.5);
        sections.addProperty("boolean", true);
        sections.add("null", JsonNull.INSTANCE);

        store.write(target, sections);

        assertEquals(sections, store.readAll(target));
        for (String name : Arrays.asList(
                "object", "array", "string", "number", "boolean", "null")) {
            assertEquals(sections.get(name), store.read(target, name));
        }
    }

    @Test
    void writeReplacesSuppliedSectionsAndPreservesUnrelatedSections() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        JsonObject initial = new JsonObject();
        initial.addProperty("replaced", "old");
        initial.addProperty("preserved", "unchanged");
        store.write(target, initial);

        JsonObject update = new JsonObject();
        update.addProperty("replaced", "new");
        update.addProperty("created", true);
        store.write(target, update);

        JsonObject expected = new JsonObject();
        expected.addProperty("replaced", "new");
        expected.addProperty("preserved", "unchanged");
        expected.addProperty("created", true);
        assertEquals(expected, store.readAll(target));
    }

    @Test
    void removeDeletesExactlyOneSectionAndPreservesJsonNullSections() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "remove");
        JsonObject initial = new JsonObject();
        initial.addProperty("removed", 1);
        initial.addProperty("preserved", 2);
        initial.add("nullable", JsonNull.INSTANCE);
        store.write(target, initial);

        store.remove(target, "removed");

        JsonObject expected = new JsonObject();
        expected.addProperty("preserved", 2);
        expected.add("nullable", JsonNull.INSTANCE);
        assertEquals(expected, store.readAll(target));
        store.remove(target, "nullable");
        assertThrows(SectionNotFoundException.class, () -> store.read(target, "nullable"));
    }

    @Test
    void removingMissingSectionFromExistingOrMissingStoreFailsWithoutCreatingDataFile()
            throws Exception {
        ResolvedPersistenceTarget existing = target(PersistenceScope.SHARED, "remove");
        store.write(existing, section("preserved", "value"));
        String before = readText(existing.getDataFile());

        assertThrows(
                SectionNotFoundException.class,
                () -> store.remove(existing, "missing"));
        assertEquals(before, readText(existing.getDataFile()));

        ResolvedPersistenceTarget missing = target(PersistenceScope.PRIVATE, "remove");
        assertThrows(
                SectionNotFoundException.class,
                () -> store.remove(missing, "missing"));
        assertFalse(Files.exists(missing.getDataFile()));
    }

    @Test
    void clearEmptiesStoreAndIsIdempotentForEmptyAndMissingStores() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "clear");
        store.write(target, section("present", true));

        store.clear(target);
        assertEquals(new JsonObject(), store.readAll(target));
        assertEquals("{}", readText(target.getDataFile()));

        store.clear(target);
        assertEquals(new JsonObject(), store.readAll(target));

        ResolvedPersistenceTarget missing = target(PersistenceScope.PRIVATE, "clear");
        store.clear(missing);
        assertEquals(new JsonObject(), store.readAll(missing));
        assertFalse(Files.exists(missing.getDataFile()));
    }

    @Test
    void malformedJsonAndEveryNonObjectRootAreRejectedAndNeverReplaced() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        Files.createDirectories(target.getDataFile().getParent());
        List<String> invalidDocuments = Arrays.asList(
                "{\"broken\":}",
                "[]",
                "\"text\"",
                "123",
                "true",
                "null");

        for (String invalidDocument : invalidDocuments) {
            writeText(target.getDataFile(), invalidDocument);

            PersistenceException readFailure = assertThrows(
                    PersistenceException.class,
                    () -> store.readAll(target),
                    invalidDocument);
            assertEquals(PersistenceException.Reason.INVALID_DATA, readFailure.getReason());

            PersistenceException writeFailure = assertThrows(
                    PersistenceException.class,
                    () -> store.write(target, section("new", "value")),
                    invalidDocument);
            assertEquals(PersistenceException.Reason.INVALID_DATA, writeFailure.getReason());
            assertEquals(invalidDocument, readText(target.getDataFile()));
        }
    }

    @Test
    void invalidExistingDataAlsoPreventsRemoveAndClear() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "clear");
        Files.createDirectories(target.getDataFile().getParent());
        writeText(target.getDataFile(), "not valid json {");

        assertEquals(
                PersistenceException.Reason.INVALID_DATA,
                assertThrows(
                        PersistenceException.class,
                        () -> store.remove(target, "anything")).getReason());
        assertEquals(
                PersistenceException.Reason.INVALID_DATA,
                assertThrows(PersistenceException.class, () -> store.clear(target)).getReason());
        assertEquals("not valid json {", readText(target.getDataFile()));
    }

    @Test
    void strictParsingRejectsJsonThatGsonLegacyModeWouldAccept() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "readAll");
        Files.createDirectories(target.getDataFile().getParent());
        writeText(target.getDataFile(), "{'singleQuoted':true}");

        PersistenceException exception = assertThrows(
                PersistenceException.class,
                () -> store.readAll(target));

        assertEquals(PersistenceException.Reason.INVALID_DATA, exception.getReason());
    }

    @Test
    void resolvedTargetDoesNotRecreateADeletedSharedApplicationNamespace() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        Files.delete(webRoot.resolve("example"));

        PersistenceException exception = assertThrows(
                PersistenceException.class,
                () -> store.write(target, section("unsafe", true)));

        assertEquals(PersistenceException.Reason.IO_FAILURE, exception.getReason());
        assertFalse(Files.exists(webRoot.resolve("example")));
    }

    @Test
    void unknownApplicationNamespaceNeverProducesATargetOrDirectory() throws Exception {
        PersistenceTargetResolver resolver = resolver();

        assertFalse(resolver.resolve("/unknown/api/shared/write").isPresent());
        assertFalse(resolver.resolve("/unknown/api/private/write").isPresent());
        assertFalse(Files.exists(webRoot.resolve("unknown")));
        assertFalse(Files.exists(privateDataRoot.resolve("unknown")));
    }

    @Test
    void symbolicPersistenceDirectoriesCannotRedirectReadsOrWrites() throws Exception {
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(outside);
        createDirectoryLink(webRoot.resolve("example/data"), outside);
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");

        assertEquals(
                PersistenceException.Reason.IO_FAILURE,
                assertThrows(PersistenceException.class, () -> store.readAll(target)).getReason());
        assertEquals(
                PersistenceException.Reason.IO_FAILURE,
                assertThrows(
                        PersistenceException.class,
                        () -> store.write(target, section("escaped", true))).getReason());
        assertFalse(Files.exists(outside.resolve("data.json")));
    }

    @Test
    void symbolicPrivateSiteDirectoryCannotRedirectAWritingTarget() throws Exception {
        Path outside = temporaryDirectory.resolve("outside-private");
        Files.createDirectories(outside);
        Files.createDirectories(privateDataRoot);
        createDirectoryLink(privateDataRoot.resolve("example"), outside);
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "write");

        PersistenceException exception = assertThrows(
                PersistenceException.class,
                () -> store.write(target, section("escaped", true)));

        assertEquals(PersistenceException.Reason.IO_FAILURE, exception.getReason());
        assertFalse(Files.exists(outside.resolve("data/data.json")));
    }

    @Test
    @Timeout(2)
    void competingWriteLockTimesOutWithoutChangingPersistenceData() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        store.write(target, section("preserved", "before"));
        String before = readText(target.getDataFile());
        JsonPersistenceStore shortTimeoutStore = new JsonPersistenceStore(60L, 5L);

        try (FileChannel channel = FileChannel.open(
                store.lockFileFor(target),
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            PersistenceException exception = assertThrows(
                    PersistenceException.class,
                    () -> shortTimeoutStore.write(target, section("preserved", "after")));

            assertEquals(PersistenceException.Reason.WRITE_LOCK_TIMEOUT, exception.getReason());
            assertEquals("Write failed", exception.getMessage());
            assertEquals(before, readText(target.getDataFile()));
        }
    }

    @Test
    void everySuccessfulModificationReleasesItsPersistenceLock() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");

        store.write(target, section("first", 1));
        assertLockIsAvailable(target);
        store.write(target, section("second", 2));
        assertLockIsAvailable(target);
        store.remove(target, "first");
        assertLockIsAvailable(target);
        store.clear(target);
        assertLockIsAvailable(target);
    }

    @Test
    void failedModificationReleasesItsPersistenceLock() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        Files.createDirectories(target.getDataFile().getParent());
        writeText(target.getDataFile(), "invalid {");

        assertThrows(
                PersistenceException.class,
                () -> store.write(target, section("value", true)));

        assertLockIsAvailable(target);
        assertEquals("invalid {", readText(target.getDataFile()));
    }

    @Test
    void persistenceLockIsAStableSidecarSeparateFromRuntimeCoordination() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        Path runtimeDirectory = temporaryDirectory.resolve("runtime");
        Files.createDirectories(runtimeDirectory);
        Path startupLock = runtimeDirectory.resolve(MiniServerStartup.STARTUP_LOCK_FILE);

        try (FileChannel channel = FileChannel.open(
                startupLock,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            store.write(target, section("value", true));
        }

        Path lockFile = store.lockFileFor(target);
        assertEquals("data.json.lock", lockFile.getFileName().toString());
        assertEquals(target.getDataFile().getParent(), lockFile.getParent());
        assertFalse(lockFile.endsWith(MiniServerStartup.STARTUP_LOCK_FILE));
        assertFalse(lockFile.endsWith(MiniServerStartup.INSTANCE_LOCK_FILE));
    }

    @Test
    void successfulModificationsAlwaysLeaveOneCompleteJsonObjectAndNoTemporaryFile()
            throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");

        store.write(target, section("one", 1));
        assertValidObjectDocument(target.getDataFile());
        store.write(target, section("two", 2));
        assertValidObjectDocument(target.getDataFile());
        store.remove(target, "one");
        assertValidObjectDocument(target.getDataFile());
        store.clear(target);
        assertValidObjectDocument(target.getDataFile());

        try (java.util.stream.Stream<Path> files = Files.list(target.getDataFile().getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void writeRejectsEmptyInputBeforeCreatingPersistenceArtifacts() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");

        assertThrows(IllegalArgumentException.class, () -> store.write(target, new JsonObject()));

        assertFalse(Files.exists(target.getDataFile().getParent()));
    }

    private ResolvedPersistenceTarget target(PersistenceScope scope, String operation)
            throws Exception {
        String path = "/example/api/"
                + (scope == PersistenceScope.SHARED ? "shared" : "private")
                + "/"
                + operation;
        return resolver().resolve(path).get();
    }

    private PersistenceTargetResolver resolver() throws IOException {
        return new PersistenceTargetResolver(webRoot, privateDataRoot);
    }

    private static JsonObject section(String name, String value) {
        JsonObject sections = new JsonObject();
        sections.addProperty(name, value);
        return sections;
    }

    private static JsonObject section(String name, boolean value) {
        JsonObject sections = new JsonObject();
        sections.addProperty(name, value);
        return sections;
    }

    private static JsonObject section(String name, int value) {
        JsonObject sections = new JsonObject();
        sections.addProperty(name, value);
        return sections;
    }

    private void assertLockIsAvailable(ResolvedPersistenceTarget target) throws Exception {
        try (FileChannel channel = FileChannel.open(
                store.lockFileFor(target),
                StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            assertNotNull(lock);
        }
    }

    private static void assertValidObjectDocument(Path file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            assertTrue(root.isJsonObject());
        }
    }

    private static String readText(Path file) throws IOException {
        return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
    }

    private static void writeText(Path file, String content) throws IOException {
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void createDirectoryLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(
                    false,
                    "Symbolic links are unavailable in this test environment.");
        }
    }
}
