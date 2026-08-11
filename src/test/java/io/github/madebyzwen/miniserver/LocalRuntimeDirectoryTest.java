package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalRuntimeDirectoryTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesRuntimeDirectoryBelowLocalAppData() throws StartupException {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("LOCALAPPDATA", temporaryDirectory.toString());

        Path resolved = LocalRuntimeDirectory.resolve(environment);

        assertEquals(
                temporaryDirectory.resolve("MiniServer").resolve("runtime"),
                resolved);
    }

    @Test
    void failsWhenLocalAppDataIsUnavailableRatherThanFallingBack() {
        assertThrows(
                StartupException.class,
                () -> LocalRuntimeDirectory.resolve(Collections.<String, String>emptyMap()));
    }

    @Test
    void rejectsRelativeLocalAppDataPath() {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("LOCALAPPDATA", "relative-profile");

        assertThrows(StartupException.class, () -> LocalRuntimeDirectory.resolve(environment));
    }
}
