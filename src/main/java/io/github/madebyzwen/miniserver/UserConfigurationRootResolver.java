package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Resolves current-user Mini Server configuration independently from persistence.
 */
final class UserConfigurationRootResolver {

    private static final String APP_DATA = "APPDATA";
    private static final String MINI_SERVER_DIRECTORY = "MiniServer";
    private static final String CONFIG_DIRECTORY = "Config";

    private UserConfigurationRootResolver() {
    }

    static Path resolve() throws IOException {
        return resolve(System.getenv());
    }

    static Path resolve(Map<String, String> environment) throws IOException {
        String appData = environment.get(APP_DATA);
        if (appData == null || appData.trim().isEmpty()) {
            throw new IOException(
                    "Cannot determine the user configuration root because APPDATA is unavailable.");
        }

        try {
            Path appDataDirectory = Paths.get(appData);
            if (!appDataDirectory.isAbsolute()) {
                throw new IOException("APPDATA must identify an absolute path.");
            }
            return appDataDirectory
                    .resolve(MINI_SERVER_DIRECTORY)
                    .resolve(CONFIG_DIRECTORY)
                    .normalize();
        } catch (InvalidPathException exception) {
            throw new IOException("APPDATA does not contain a valid filesystem path.", exception);
        } catch (SecurityException exception) {
            throw new IOException("The user configuration root cannot be accessed.", exception);
        }
    }
}
