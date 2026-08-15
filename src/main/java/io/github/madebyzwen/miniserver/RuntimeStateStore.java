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
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

final class RuntimeStateStore {

    private static final Gson GSON = new Gson();
    private static final String PORT_PROPERTY = "port";
    private static final String STOP_TOKEN_PROPERTY = "stopToken";
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
        Optional<JsonObject> storedState = readObject();
        if (!storedState.isPresent()) {
            return OptionalInt.empty();
        }

        return readPort(storedState.get());
    }

    Optional<State> readState() throws IOException {
        Optional<JsonObject> storedState = readObject();
        if (!storedState.isPresent()) {
            return Optional.empty();
        }

        JsonObject state = storedState.get();
        OptionalInt port = readPort(state);
        JsonElement tokenElement = state.get(STOP_TOKEN_PROPERTY);
        if (!port.isPresent()
                || tokenElement == null
                || !tokenElement.isJsonPrimitive()
                || !tokenElement.getAsJsonPrimitive().isString()) {
            return Optional.empty();
        }

        String stopToken = tokenElement.getAsString();
        if (!isValidStopToken(stopToken)) {
            return Optional.empty();
        }
        return Optional.of(new State(port.getAsInt(), stopToken));
    }

    private Optional<JsonObject> readObject() throws IOException {
        if (!Files.isRegularFile(stateFile)) {
            return Optional.empty();
        }

        try (BufferedReader reader = Files.newBufferedReader(stateFile, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            return root.isJsonObject()
                    ? Optional.of(root.getAsJsonObject())
                    : Optional.<JsonObject>empty();
        } catch (JsonParseException exception) {
            return Optional.empty();
        }
    }

    private static OptionalInt readPort(JsonObject state) {
        JsonElement portElement = state.get(PORT_PROPERTY);
        if (portElement == null
                || !portElement.isJsonPrimitive()
                || !portElement.getAsJsonPrimitive().isNumber()) {
            return OptionalInt.empty();
        }

        try {
            BigDecimal numericPort = new BigDecimal(portElement.getAsString());
            long port = numericPort.longValueExact();
            return isValidPort(port)
                    ? OptionalInt.of((int) port)
                    : OptionalInt.empty();
        } catch (NumberFormatException | ArithmeticException exception) {
            return OptionalInt.empty();
        }
    }

    void writeState(int port, String stopToken) throws IOException {
        if (!isValidPort(port)) {
            throw new IllegalArgumentException("The active server port is outside the valid TCP range.");
        }
        if (!isValidStopToken(stopToken)) {
            throw new IllegalArgumentException("The active server stop token is invalid.");
        }

        JsonObject state = new JsonObject();
        state.addProperty(PORT_PROPERTY, port);
        state.addProperty(STOP_TOKEN_PROPERTY, stopToken);
        writeObject(state);
    }

    private void writeObject(JsonObject state) throws IOException {

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

    private static boolean isValidStopToken(String stopToken) {
        if (stopToken == null || stopToken.length() != 36) {
            return false;
        }
        try {
            return UUID.fromString(stopToken).toString().equals(stopToken);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static final class State {

        private final int port;
        private final String stopToken;

        private State(int port, String stopToken) {
            this.port = port;
            this.stopToken = stopToken;
        }

        int getPort() {
            return port;
        }

        String getStopToken() {
            return stopToken;
        }
    }
}
