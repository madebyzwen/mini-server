package io.github.madebyzwen.miniserver;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Locale;

final class WebRootResolver {

    private static final String WEB_ROOT_DIRECTORY = "www";

    private WebRootResolver() {
    }

    static Path resolve() throws StartupException {
        URL codeLocation = null;
        try {
            CodeSource codeSource = MiniServer.class.getProtectionDomain().getCodeSource();
            if (codeSource != null) {
                codeLocation = codeSource.getLocation();
            }
        } catch (SecurityException exception) {
            throw new StartupException("The Mini Server installation location cannot be accessed.", exception);
        }

        return resolve(codeLocation, Paths.get(""));
    }

    static Path resolve(URL codeLocation, Path workingDirectory) throws StartupException {
        if (codeLocation != null && "file".equalsIgnoreCase(codeLocation.getProtocol())) {
            try {
                URI locationUri = codeLocation.toURI();
                Path codePath = Paths.get(locationUri).toAbsolutePath().normalize();
                Path fileName = codePath.getFileName();
                if (Files.isRegularFile(codePath)
                        && fileName != null
                        && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    Path installationDirectory = codePath.getParent();
                    if (installationDirectory == null) {
                        throw new StartupException(
                                "The Mini Server installation directory cannot be determined.");
                    }
                    return installationDirectory.resolve(WEB_ROOT_DIRECTORY).normalize();
                }
            } catch (URISyntaxException | IllegalArgumentException exception) {
                throw new StartupException(
                        "The Mini Server installation location is not a valid filesystem path.",
                        exception);
            }
        }

        return workingDirectory.toAbsolutePath().normalize().resolve(WEB_ROOT_DIRECTORY);
    }
}
