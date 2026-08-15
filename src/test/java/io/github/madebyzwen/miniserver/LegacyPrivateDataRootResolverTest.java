package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegacyPrivateDataRootResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesOnlyTheHistoricalMiniServerDataRoot() throws Exception {
        assertEquals(
                temporaryDirectory.resolve("MiniServerData"),
                LegacyPrivateDataRootResolver.resolve(
                        Collections.singletonMap("APPDATA", temporaryDirectory.toString())));
    }

    @Test
    void rejectsUnavailableAppData() {
        assertThrows(
                IOException.class,
                () -> LegacyPrivateDataRootResolver.resolve(Collections.<String, String>emptyMap()));
    }
}
