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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

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
    private Path legacyPrivateDataRoot;
    private JsonPersistenceStore store;

    @BeforeEach
    void createPersistenceContext() throws IOException {
        webRoot = temporaryDirectory.resolve("www");
        privateDataRoot = temporaryDirectory.resolve("profile/MiniServer/Data");
        legacyPrivateDataRoot = temporaryDirectory.resolve("profile/LegacyMiniServerData");
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
                privateDataRoot.resolve("example/data.json").toRealPath(),
                privateTarget.getDataFile().toRealPath());
    }

    @Test
    void privateReadMigratesLegacyBytesBeforeReadingAndCleansEmptyLegacyDirectories()
            throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "readAll");
        String legacyBytes = "{\n  \"profile\": {\"name\": \"Sven\"}\n}\n";
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), legacyBytes);

        assertEquals("Sven", store.readAll(target)
                .getAsJsonObject("profile").get("name").getAsString());
        assertEquals(legacyBytes, readText(target.getDataFile()));
        assertFalse(Files.exists(target.getLegacyDataFile()));
        assertFalse(Files.exists(legacyPrivateDataRoot));
    }

    @Test
    void privateWriteMigratesLegacyDocumentThenUpdatesOnlyCanonicalData() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "write");
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), "{\"preserved\":1,\"changed\":\"old\"}");

        store.write(target, section("changed", "new"));

        assertEquals(1, store.readAll(target).get("preserved").getAsInt());
        assertEquals("new", store.readAll(target).get("changed").getAsString());
        assertFalse(Files.exists(target.getLegacyDataFile()));
    }

    @Test
    void indeterminateLegacyInspectionFailsPrivateWriteWithoutCreatingCanonicalData()
            throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "write");
        Files.createDirectories(target.getLegacyDataFile().getParent());
        String legacyBytes = "{\"preserved\":true}";
        writeText(target.getLegacyDataFile(), legacyBytes);
        JsonPersistenceStore failingStore = new JsonPersistenceStore(
                1000L,
                10L,
                file -> {
                    if (file.equals(target.getLegacyDataFile())) {
                        throw new IOException("deliberate legacy inspection failure");
                    }
                    return JsonPersistenceStore.probeFilePresence(file);
                });

        PersistenceException failure = assertThrows(
                PersistenceException.class,
                () -> failingStore.write(target, section("new", true)));

        assertEquals(PersistenceException.Reason.IO_FAILURE, failure.getReason());
        assertEquals("Write failed", failure.getMessage());
        assertFalse(Files.exists(target.getDataFile()));
        assertEquals(legacyBytes, readText(target.getLegacyDataFile()));
    }

    @Test
    void canonicalPrivateDataWinsAndLegacyDataIsNotTouched() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "readAll");
        Files.createDirectories(target.getDataFile().getParent());
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getDataFile(), "{\"source\":\"canonical\"}");
        writeText(target.getLegacyDataFile(), "{\"source\":\"legacy\"}");
        JsonPersistenceStore canonicalStore = new JsonPersistenceStore(
                1000L,
                10L,
                file -> {
                    if (file.equals(target.getLegacyDataFile())) {
                        throw new IOException("legacy must not be inspected when canonical exists");
                    }
                    return JsonPersistenceStore.probeFilePresence(file);
                });

        assertEquals("canonical", canonicalStore.readAll(target).get("source").getAsString());
        assertTrue(Files.exists(target.getLegacyDataFile()));
        assertEquals("{\"source\":\"legacy\"}", readText(target.getLegacyDataFile()));
    }

    @Test
    @Timeout(5)
    void migrationRechecksCanonicalAfterWaitingForItsLock() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "readAll");
        Files.createDirectories(target.getDataFile().getParent());
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), "{\"source\":\"legacy\"}");
        Path lockFile = store.lockFileFor(target);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (FileChannel channel = FileChannel.open(
                lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            Future<JsonObject> read = executor.submit(() -> store.readAll(target));
            Thread.sleep(50L);
            writeText(target.getDataFile(), "{\"source\":\"canonical\"}");
            ignored.release();
            assertEquals("canonical", read.get().get("source").getAsString());
        } finally {
            executor.shutdownNow();
        }
        assertTrue(Files.exists(target.getLegacyDataFile()));
    }

    @Test
    void migrationPublicationCannotReplaceCanonicalThatAppearsAfterLockedRecheck()
            throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "readAll");
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), "{\"source\":\"legacy\"}");
        AtomicInteger legacyProbes = new AtomicInteger();
        JsonPersistenceStore conflictingStore = new JsonPersistenceStore(
                1000L,
                10L,
                file -> {
                    boolean present = JsonPersistenceStore.probeFilePresence(file);
                    if (file.equals(target.getLegacyDataFile())
                            && present
                            && legacyProbes.incrementAndGet() == 2) {
                        Files.createDirectories(target.getDataFile().getParent());
                        writeText(target.getDataFile(), "{\"source\":\"canonical\"}");
                    }
                    return present;
                });

        assertThrows(PersistenceException.class, () -> conflictingStore.readAll(target));

        assertEquals("{\"source\":\"canonical\"}", readText(target.getDataFile()));
        assertEquals("{\"source\":\"legacy\"}", readText(target.getLegacyDataFile()));
    }

    @Test
    void unsafeLegacyPathFailsWithoutDestroyingLegacySource() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "readAll");
        Path outside = temporaryDirectory.resolve("outside-legacy");
        Files.createDirectories(outside);
        writeText(outside.resolve("data.json"), "{\"legacy\":true}");
        Files.createDirectories(legacyPrivateDataRoot.resolve("example"));
        createDirectoryLink(legacyPrivateDataRoot.resolve("example/data"), outside);

        assertThrows(PersistenceException.class, () -> store.readAll(target));
        assertTrue(Files.exists(outside.resolve("data.json")));
        assertFalse(Files.exists(target.getDataFile()));
    }

    @Test
    void invalidMigratedJsonRemainsByteExactAndFailsNormalValidation() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "readAll");
        String invalid = "{\"broken\": }\n";
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), invalid);

        PersistenceException failure = assertThrows(
                PersistenceException.class,
                () -> store.readAll(target));

        assertEquals(PersistenceException.Reason.INVALID_DATA, failure.getReason());
        assertEquals(invalid, readText(target.getDataFile()));
        assertFalse(Files.exists(target.getLegacyDataFile()));
    }

    @Test
    void successfulMigrationLeavesUnrelatedLegacyContentAndCleanupFailureIsNonfatal()
            throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "readAll");
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), "{\"migrated\":true}");
        Path unrelated = legacyPrivateDataRoot.resolve("keep.txt");
        writeText(unrelated, "user content");

        assertTrue(store.readAll(target).get("migrated").getAsBoolean());

        assertFalse(Files.exists(target.getLegacyDataFile()));
        assertTrue(Files.exists(unrelated));
        assertEquals("user content", readText(unrelated));
        assertTrue(Files.isDirectory(legacyPrivateDataRoot));
    }

    @Test
    void laterPrivateOperationsNeverWriteOrFallBackToRecreatedLegacyData() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.PRIVATE, "write");
        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), "{\"source\":\"original legacy\"}");
        store.write(target, section("canonicalOnly", true));

        Files.createDirectories(target.getLegacyDataFile().getParent());
        writeText(target.getLegacyDataFile(), "{\"source\":\"new legacy\"}");
        store.write(target, section("later", true));

        assertEquals("original legacy", store.readAll(target).get("source").getAsString());
        assertTrue(store.readAll(target).get("canonicalOnly").getAsBoolean());
        assertTrue(store.readAll(target).get("later").getAsBoolean());
        assertEquals("{\"source\":\"new legacy\"}", readText(target.getLegacyDataFile()));
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
    void trailingGarbageAndMultipleTopLevelValuesAreRejectedAndNeverReplaced()
            throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        Files.createDirectories(target.getDataFile().getParent());
        List<String> invalidDocuments = Arrays.asList(
                "{\"valid\":true} garbage",
                "{\"valid\":true}\n{\"second\":true}");

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
    void completeObjectWithTrailingWhitespaceRemainsValid() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "readAll");
        Files.createDirectories(target.getDataFile().getParent());
        writeText(target.getDataFile(), "{\"valid\":true}\n\t  ");

        JsonObject expected = new JsonObject();
        expected.addProperty("valid", true);
        assertEquals(expected, store.readAll(target));
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
        assertFalse(Files.exists(outside.resolve("data.json")));
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
    @Timeout(3)
    void competingWriteLockAlsoBoundsRemoveAndClearWithoutChangingData() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "remove");
        store.write(target, section("preserved", "before"));
        String before = readText(target.getDataFile());
        JsonPersistenceStore shortTimeoutStore = new JsonPersistenceStore(60L, 5L);

        try (FileChannel channel = FileChannel.open(
                store.lockFileFor(target),
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            PersistenceException removeFailure = assertThrows(
                    PersistenceException.class,
                    () -> shortTimeoutStore.remove(target, "preserved"));
            assertEquals(
                    PersistenceException.Reason.WRITE_LOCK_TIMEOUT,
                    removeFailure.getReason());
            assertEquals("Write failed", removeFailure.getMessage());
            assertEquals(before, readText(target.getDataFile()));
        }

        try (FileChannel channel = FileChannel.open(
                store.lockFileFor(target),
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            PersistenceException clearFailure = assertThrows(
                    PersistenceException.class,
                    () -> shortTimeoutStore.clear(target));
            assertEquals(
                    PersistenceException.Reason.WRITE_LOCK_TIMEOUT,
                    clearFailure.getReason());
            assertEquals("Write failed", clearFailure.getMessage());
            assertEquals(before, readText(target.getDataFile()));
        }
    }

    @Test
    @Timeout(2)
    void lockContentionOnOneTargetDoesNotBlockAnUnrelatedTarget() throws Exception {
        Files.createDirectories(webRoot.resolve("dashboard"));
        ResolvedPersistenceTarget example = target(PersistenceScope.SHARED, "write");
        ResolvedPersistenceTarget dashboard = resolver()
                .resolve("/dashboard/api/shared/write")
                .get();
        store.write(example, section("example", true));

        try (FileChannel channel = FileChannel.open(
                store.lockFileFor(example),
                StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            store.write(dashboard, section("dashboard", true));
        }

        assertEquals(section("example", true), store.readAll(example));
        assertEquals(section("dashboard", true), store.readAll(dashboard));
        assertFalse(store.lockFileFor(example).equals(store.lockFileFor(dashboard)));
    }

    @Test
    @Timeout(10)
    void concurrentReadersObserveOnlyCompleteObjectsDuringAtomicWrites() throws Exception {
        ResolvedPersistenceTarget target = target(PersistenceScope.SHARED, "write");
        JsonObject first = largeDocument("first", 'a');
        JsonObject second = largeDocument("second", 'b');
        store.write(target, first);

        CountDownLatch start = new CountDownLatch(1);
        AtomicBoolean writerFinished = new AtomicBoolean(false);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> writer = executor.submit(() -> {
                start.await();
                try {
                    for (int index = 0; index < 40; index++) {
                        store.write(target, index % 2 == 0 ? second : first);
                    }
                } finally {
                    writerFinished.set(true);
                }
                return null;
            });
            Future<Integer> reader = executor.submit(() -> {
                start.await();
                int completeReads = 0;
                do {
                    JsonObject observed = store.readAll(target);
                    if (!first.equals(observed) && !second.equals(observed)) {
                        throw new AssertionError(
                                "A reader observed a partial persistence document: " + observed);
                    }
                    completeReads++;
                } while (!writerFinished.get() || completeReads < 40);
                return completeReads;
            });

            start.countDown();
            writer.get(8L, TimeUnit.SECONDS);
            assertTrue(reader.get(8L, TimeUnit.SECONDS) >= 40);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
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
        return new PersistenceTargetResolver(webRoot, privateDataRoot, legacyPrivateDataRoot);
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

    private static JsonObject largeDocument(String generation, char fill) {
        char[] payloadCharacters = new char[64 * 1024];
        Arrays.fill(payloadCharacters, fill);
        JsonObject value = new JsonObject();
        value.addProperty("generation", generation);
        value.addProperty("payload", new String(payloadCharacters));
        JsonObject document = new JsonObject();
        document.add("state", value);
        return document;
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
