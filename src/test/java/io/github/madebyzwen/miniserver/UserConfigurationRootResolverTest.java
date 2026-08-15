package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserConfigurationRootResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void resolvesMiniServerConfigurationBelowAbsoluteAppDataWithoutCreatingIt()
            throws Exception {
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("APPDATA", temporaryDirectory.toString());

        Path resolved = UserConfigurationRootResolver.resolve(environment);

        assertEquals(
                temporaryDirectory.resolve("MiniServer/Config").normalize(),
                resolved);
        assertFalse(resolved.toString().contains("MiniServerData"));
        assertFalse(resolved.toFile().exists());
    }

    @Test
    void rejectsMissingEmptyAndRelativeAppData() {
        assertThrows(
                IOException.class,
                () -> UserConfigurationRootResolver.resolve(Collections.emptyMap()));

        Map<String, String> empty = new HashMap<String, String>();
        empty.put("APPDATA", "   ");
        assertThrows(IOException.class, () -> UserConfigurationRootResolver.resolve(empty));

        Map<String, String> relative = new HashMap<String, String>();
        relative.put("APPDATA", "relative/app-data");
        assertThrows(IOException.class, () -> UserConfigurationRootResolver.resolve(relative));
    }
}
