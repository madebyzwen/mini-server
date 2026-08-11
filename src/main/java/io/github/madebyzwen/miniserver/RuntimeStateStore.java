package io.github.madebyzwen.miniserver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.OptionalInt;

final class RuntimeStateStore {

    private static final Gson GSON = new Gson();
    private static final String PORT_PROPERTY = "port";
    private static final int MIN_PORT = 1;
    private static final int MAX_PORT = 65535;

    private final Path runtimeDirectory;
    private final Path stateFile;

    RuntimeStateStore(Path runtimeDirectory) {
        this.runtimeDirectory = runtimeDirectory;
        this.stateFile = runtimeDirectory.resolve(MiniServerStartup.INSTANCE_STATE_FILE);
    }

    Path getStateFile() {
        return stateFile;
    }

    void invalidate() throws IOException {
        Files.deleteIfExists(stateFile);
    }

    OptionalInt readPort() throws IOException {
        if (!Files.isRegularFile(stateFile)) {
            return OptionalInt.empty();
        }

        try (BufferedReader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                return OptionalInt.empty();
            }

            JsonElement portElement = root.getAsJsonObject().get(PORT_PROPERTY);
            if (portElement == null
                    || !portElement.isJsonPrimitive()
                    || !portElement.getAsJsonPrimitive().isNumber()) {
                return OptionalInt.empty();
            }

            BigDecimal numericPort = new BigDecimal(portElement.getAsString());
            long port = numericPort.longValueExact();
            if (!isValidPort(port)) {
                return OptionalInt.empty();
            }
            return OptionalInt.of((int) port);
        } catch (JsonParseException | NumberFormatException | ArithmeticException exception) {
            return OptionalInt.empty();
        }
    }

    void writePort(int port) throws IOException {
        if (!isValidPort(port)) {
            throw new IllegalArgumentException("The active server port is outside the valid TCP range.");
        }

        JsonObject state = new JsonObject();
        state.addProperty(PORT_PROPERTY, port);

        Path temporaryFile = Files.createTempFile(runtimeDirectory, "instance-", ".tmp");
        boolean moved = false;
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }

            try {
                Files.move(
                        temporaryFile,
                        stateFile,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporaryFile, stateFile, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    private static boolean isValidPort(long port) {
        return port >= MIN_PORT && port <= MAX_PORT;
    }
}
