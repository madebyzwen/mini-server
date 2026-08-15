package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrivateDataRootResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesPrivateRootBelowAppData() throws Exception {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("APPDATA", temporaryDirectory.toString());

        Path privateRoot = PrivateDataRootResolver.resolve(environment);

        assertEquals(temporaryDirectory.resolve("MiniServer/Data"), privateRoot);
    }

    @Test
    void unavailableAppDataFailsWithoutFallback() {
        IOException exception = assertThrows(
                IOException.class,
                () -> PrivateDataRootResolver.resolve(
                        Collections.<String, String>emptyMap()));

        assertEquals(
                "Cannot determine the private data root because APPDATA is unavailable.",
                exception.getMessage());
    }

    @Test
    void relativeAppDataIsRejected() {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("APPDATA", "relative-profile");

        assertThrows(IOException.class, () -> PrivateDataRootResolver.resolve(environment));
    }
}
