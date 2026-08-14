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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Stores JSON Sections in a server-resolved persistence target.
 */
final class JsonPersistenceStore extends PersistenceStore {

    private static final Gson GSON = new GsonBuilder().serializeNulls().create();
    private static final long DEFAULT_LOCK_TIMEOUT_MILLIS = 1000L;
    private static final long DEFAULT_LOCK_RETRY_MILLIS = 10L;
    private static final String LOCK_SUFFIX = ".lock";

    private final long lockTimeoutNanos;
    private final long lockRetryNanos;

    JsonPersistenceStore() {
        this(DEFAULT_LOCK_TIMEOUT_MILLIS, DEFAULT_LOCK_RETRY_MILLIS);
    }

    JsonPersistenceStore(long lockTimeoutMillis, long lockRetryMillis) {
        if (lockTimeoutMillis < 0L) {
            throw new IllegalArgumentException("The persistence lock timeout must not be negative.");
        }
        if (lockRetryMillis <= 0L) {
            throw new IllegalArgumentException("The persistence lock retry interval must be positive.");
        }
        this.lockTimeoutNanos = TimeUnit.MILLISECONDS.toNanos(lockTimeoutMillis);
        this.lockRetryNanos = TimeUnit.MILLISECONDS.toNanos(lockRetryMillis);
    }

    JsonElement read(ResolvedPersistenceTarget target, String section)
            throws PersistenceException, SectionNotFoundException {
        requireTarget(target);
        requireSection(section);

        JsonObject root = readRoot(target);
        if (!root.has(section)) {
            throw new SectionNotFoundException();
        }
        return root.get(section).deepCopy();
    }

    JsonObject readAll(ResolvedPersistenceTarget target) throws PersistenceException {
        requireTarget(target);
        return readRoot(target);
    }

    void write(ResolvedPersistenceTarget target, JsonObject sections)
            throws PersistenceException {
        requireTarget(target);
        if (sections == null || sections.size() == 0) {
            throw new IllegalArgumentException("At least one persistence Section is required.");
        }

        modify(target, new RootModification() {
            @Override
            public boolean apply(JsonObject root) {
                for (Map.Entry<String, JsonElement> section : sections.entrySet()) {
                    root.add(section.getKey(), section.getValue().deepCopy());
                }
                return true;
            }
        });
    }

    void remove(ResolvedPersistenceTarget target, final String section)
            throws PersistenceException, SectionNotFoundException {
        requireTarget(target);
        requireSection(section);

        boolean removed = modify(target, new RootModification() {
            @Override
            public boolean apply(JsonObject root) {
                if (!root.has(section)) {
                    return false;
                }
                root.remove(section);
                return true;
            }
        });
        if (!removed) {
            throw new SectionNotFoundException();
        }
    }

    void clear(ResolvedPersistenceTarget target) throws PersistenceException {
        requireTarget(target);
        modify(target, new RootModification() {
            @Override
            public boolean apply(JsonObject root) {
                if (root.size() == 0) {
                    return false;
                }
                root.entrySet().clear();
                return true;
            }
        });
    }

    Path lockFileFor(ResolvedPersistenceTarget target) {
        requireTarget(target);
        Path dataFile = target.getDataFile();
        return dataFile.resolveSibling(dataFile.getFileName().toString() + LOCK_SUFFIX);
    }

    private boolean modify(ResolvedPersistenceTarget target, RootModification modification)
            throws PersistenceException {
        Path dataFile = target.getDataFile();
        Path dataDirectory = prepareDataDirectory(target);
        Path lockFile = lockFileFor(target);
        if (Files.isSymbolicLink(lockFile)) {
            throw writeFailure(null);
        }

        OpenOption[] lockOptions = {
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE
        };
        try (FileChannel lockChannel = FileChannel.open(lockFile, lockOptions);
             FileLock ignored = acquireLock(lockChannel)) {
            JsonObject root = readRoot(target);
            boolean shouldWrite = modification.apply(root);
            if (shouldWrite) {
                writeAtomically(dataDirectory, dataFile, root);
            }
            return shouldWrite;
        } catch (PersistenceException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw new PersistenceException(
                    PersistenceException.Reason.IO_FAILURE,
                    "Write failed",
                    exception);
        }
    }

    private Path prepareDataDirectory(ResolvedPersistenceTarget target)
            throws PersistenceException {
        Path dataFile = target.getDataFile();
        Path dataDirectory = dataFile.getParent();
        if (dataDirectory == null) {
            throw writeFailure(null);
        }

        try {
            if (target.getScope() == PersistenceScope.SHARED) {
                Path siteDirectory = dataDirectory.getParent();
                if (siteDirectory == null
                        || !Files.isDirectory(siteDirectory, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(siteDirectory)) {
                    throw writeFailure(null);
                }
                createSingleDirectoryIfMissing(dataDirectory);
            } else {
                preparePrivateDataDirectory(dataDirectory);
            }

            if (!Files.isDirectory(dataDirectory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(dataDirectory)
                    || Files.isSymbolicLink(dataFile)) {
                throw writeFailure(null);
            }
            return dataDirectory;
        } catch (PersistenceException exception) {
            throw exception;
        } catch (IOException | SecurityException exception) {
            throw writeFailure(exception);
        }
    }

    private static void createSingleDirectoryIfMissing(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            Files.createDirectory(directory);
        } catch (FileAlreadyExistsException exception) {
            // A concurrent writer may have created the same directory.
        }
    }

    private static void preparePrivateDataDirectory(Path dataDirectory)
            throws IOException, PersistenceException {
        Path siteDirectory = dataDirectory.getParent();
        Path privateDataRoot = siteDirectory == null ? null : siteDirectory.getParent();
        if (privateDataRoot == null) {
            throw writeFailure(null);
        }

        Files.createDirectories(privateDataRoot);
        if (!Files.isDirectory(privateDataRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(privateDataRoot)) {
            throw writeFailure(null);
        }

        createSingleDirectoryIfMissing(siteDirectory);
        if (!Files.isDirectory(siteDirectory, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(siteDirectory)) {
            throw writeFailure(null);
        }
        createSingleDirectoryIfMissing(dataDirectory);
    }

    private FileLock acquireLock(FileChannel channel) throws PersistenceException {
        long started = System.nanoTime();
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException exception) {
                // Another thread in this JVM currently owns the persistence lock.
            } catch (IOException exception) {
                throw writeFailure(exception);
            }

            long elapsed = System.nanoTime() - started;
            if (elapsed >= lockTimeoutNanos) {
                throw new PersistenceException(
                        PersistenceException.Reason.WRITE_LOCK_TIMEOUT,
                        "Write failed");
            }

            long remaining = lockTimeoutNanos - elapsed;
            LockSupport.parkNanos(Math.min(lockRetryNanos, remaining));
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new PersistenceException(
                        PersistenceException.Reason.WRITE_LOCK_TIMEOUT,
                        "Write failed");
            }
        }
    }

    private static JsonObject readRoot(ResolvedPersistenceTarget target)
            throws PersistenceException {
        Path dataFile = target.getDataFile();
        validateReadablePath(target);
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    dataFile,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                throw readFailure(null);
            }
        } catch (NoSuchFileException exception) {
            return new JsonObject();
        } catch (IOException | SecurityException exception) {
            throw readFailure(exception);
        }

        try (BufferedReader reader = Files.newBufferedReader(dataFile, StandardCharsets.UTF_8)) {
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.setStrictness(Strictness.STRICT);
            JsonElement root = JsonParser.parseReader(jsonReader);
            if (!root.isJsonObject() || jsonReader.peek() != JsonToken.END_DOCUMENT) {
                throw invalidData(null);
            }
            return root.getAsJsonObject();
        } catch (NoSuchFileException exception) {
            return new JsonObject();
        } catch (MalformedJsonException exception) {
            throw invalidData(exception);
        } catch (JsonParseException exception) {
            throw invalidData(exception);
        } catch (IOException | SecurityException exception) {
            throw readFailure(exception);
        }
    }

    private static void validateReadablePath(ResolvedPersistenceTarget target)
            throws PersistenceException {
        Path dataDirectory = target.getDataFile().getParent();
        Path siteDirectory = dataDirectory == null ? null : dataDirectory.getParent();
        if (dataDirectory == null || siteDirectory == null) {
            throw readFailure(null);
        }

        rejectExistingLinkOrNonDirectory(siteDirectory);
        rejectExistingLinkOrNonDirectory(dataDirectory);
        if (target.getScope() == PersistenceScope.PRIVATE) {
            Path privateDataRoot = siteDirectory.getParent();
            if (privateDataRoot == null) {
                throw readFailure(null);
            }
            rejectExistingLinkOrNonDirectory(privateDataRoot);
        }
    }

    private static void rejectExistingLinkOrNonDirectory(Path directory)
            throws PersistenceException {
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                    && (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(directory))) {
                throw readFailure(null);
            }
        } catch (SecurityException exception) {
            throw readFailure(exception);
        }
    }

    private static void writeAtomically(Path dataDirectory, Path dataFile, JsonObject root)
            throws PersistenceException {
        Path temporaryFile = null;
        boolean moved = false;
        try {
            temporaryFile = Files.createTempFile(
                    dataDirectory,
                    dataFile.getFileName().toString() + "-",
                    ".tmp");
            try (BufferedWriter writer =
                         Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            Files.move(
                    temporaryFile,
                    dataFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } catch (IOException | SecurityException exception) {
            throw writeFailure(exception);
        } finally {
            if (!moved && temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException | SecurityException ignored) {
                    // Preserve the original write failure.
                }
            }
        }
    }

    private static void requireTarget(ResolvedPersistenceTarget target) {
        if (target == null || target.getDataFile() == null || target.getScope() == null) {
            throw new IllegalArgumentException("A resolved persistence target is required.");
        }
    }

    private static void requireSection(String section) {
        if (section == null) {
            throw new IllegalArgumentException("A persistence Section is required.");
        }
    }

    private static PersistenceException invalidData(Throwable cause) {
        return new PersistenceException(
                PersistenceException.Reason.INVALID_DATA,
                "Persistence data is invalid.",
                cause);
    }

    private static PersistenceException readFailure(Throwable cause) {
        return new PersistenceException(
                PersistenceException.Reason.IO_FAILURE,
                "Persistence data could not be read.",
                cause);
    }

    private static PersistenceException writeFailure(Throwable cause) {
        return new PersistenceException(
                PersistenceException.Reason.IO_FAILURE,
                "Write failed",
                cause);
    }

    private interface RootModification {
        boolean apply(JsonObject root);
    }
}
