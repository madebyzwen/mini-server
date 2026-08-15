package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStateStoreTest {

    @TempDir
    Path runtimeDirectory;

    @Test
    void roundTripsPortAndStopTokenAsOneConsistentState() throws Exception {
        RuntimeStateStore stateStore = new RuntimeStateStore(runtimeDirectory);
        String stopToken = UUID.randomUUID().toString();

        stateStore.writeState(51847, stopToken);
        Optional<RuntimeStateStore.State> storedState = stateStore.readState();

        assertTrue(storedState.isPresent());
        assertEquals(51847, storedState.get().getPort());
        assertEquals(stopToken, storedState.get().getStopToken());
        assertEquals(51847, stateStore.readPort().getAsInt());
    }

    @Test
    void rejectsMalformedIncompleteNonIntegralAndOutOfRangePorts() throws Exception {
        RuntimeStateStore stateStore = new RuntimeStateStore(runtimeDirectory);
        List<String> invalidStates = Arrays.asList(
                "not-json",
                "{}",
                "{\"port\":\"12345\"}",
                "{\"port\":1.5}",
                "{\"port\":0}",
                "{\"port\":65536}");

        for (String invalidState : invalidStates) {
            Files.write(
                    stateStore.getStateFile(),
                    invalidState.getBytes(StandardCharsets.UTF_8));

            assertFalse(
                    stateStore.readPort().isPresent(),
                    "State should have been rejected: " + invalidState);
        }
    }

    @Test
    void rejectsMalformedOrIncompleteStopState() throws Exception {
        RuntimeStateStore stateStore = new RuntimeStateStore(runtimeDirectory);
        List<String> invalidStates = Arrays.asList(
                "not-json",
                "{\"port\":51847}",
                "{\"port\":51847,\"stopToken\":\"\"}",
                "{\"port\":51847,\"stopToken\":\"not-a-token\"}");

        for (String invalidState : invalidStates) {
            Files.write(
                    stateStore.getStateFile(),
                    invalidState.getBytes(StandardCharsets.UTF_8));

            assertFalse(
                    stateStore.readState().isPresent(),
                    "State should have been rejected: " + invalidState);
        }
    }
}
