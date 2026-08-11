package io.github.madebyzwen.miniserver;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

final class LocalRuntimeDirectory {

    private static final String LOCAL_APP_DATA = "LOCALAPPDATA";

    private LocalRuntimeDirectory() {
    }

    static Path resolve() throws StartupException {
        return resolve(System.getenv());
    }

    static Path resolve(Map<String, String> environment) throws StartupException {
        String localAppData = environment.get(LOCAL_APP_DATA);
        if (localAppData == null || localAppData.trim().isEmpty()) {
            throw new StartupException(
                    "Cannot determine the local runtime directory because LOCALAPPDATA is unavailable.");
        }

        try {
            Path baseDirectory = Paths.get(localAppData);
            if (!baseDirectory.isAbsolute()) {
                throw new StartupException("LOCALAPPDATA must identify an absolute path.");
            }
            return baseDirectory.resolve("MiniServer").resolve("runtime").normalize();
        } catch (InvalidPathException exception) {
            throw new StartupException("LOCALAPPDATA does not contain a valid filesystem path.", exception);
        } catch (SecurityException exception) {
            throw new StartupException("The local runtime directory cannot be accessed.", exception);
        }
    }
}
