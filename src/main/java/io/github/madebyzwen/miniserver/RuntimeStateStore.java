package io.github.madebyzwen.miniserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.OptionalInt;

final class RuntimeStateStore {
    private final Gson gson = new GsonBuilder()
            .setStrictness(Strictness.STRICT)
            .setPrettyPrinting()
            .create();
    private final Path runtimeDirectory;
    private final Path stateFile;

    RuntimeStateStore(Path runtimeDirectory) {
        this.runtimeDirectory = runtimeDirectory;
        this.stateFile = runtimeDirectory.resolve("instance.json");
    }

    void invalidate() throws IOException {
        Files.deleteIfExists(stateFile);
    }

    OptionalInt readPort() {
        if (!Files.isRegularFile(stateFile)) {
            return OptionalInt.empty();
        }

        try (Reader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            JsonElement root = gson.fromJson(reader, JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                return OptionalInt.empty();
            }

            JsonObject object = root.getAsJsonObject();
            JsonElement portElement = object.get("port");
            if (portElement == null || !portElement.isJsonPrimitive()) {
                return OptionalInt.empty();
            }

            JsonPrimitive portPrimitive = portElement.getAsJsonPrimitive();
            if (!portPrimitive.isNumber()) {
                return OptionalInt.empty();
            }

            int port = new BigDecimal(portPrimitive.getAsString()).intValueExact();
            if (port < 1 || port > 65_535) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(port);
        } catch (IOException | RuntimeException exception) {
            return OptionalInt.empty();
        }
    }

    void publish(int port) throws IOException {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 1 and 65535");
        }

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(runtimeDirectory, "instance-", ".json.tmp");
            try (Writer writer = Files.newBufferedWriter(
                    temporaryFile,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                gson.toJson(new RuntimeState(port), writer);
            }

            moveIntoPlace(temporaryFile);
            temporaryFile = null;
        } finally {
            if (temporaryFile != null) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private void moveIntoPlace(Path temporaryFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    stateFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class RuntimeState {
        private final int port;

        private RuntimeState(int port) {
            this.port = port;
        }
    }
}
