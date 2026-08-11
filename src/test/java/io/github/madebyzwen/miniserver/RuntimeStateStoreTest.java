package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RuntimeStateStoreTest {

    @TempDir
    Path runtimeDirectory;

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
}
