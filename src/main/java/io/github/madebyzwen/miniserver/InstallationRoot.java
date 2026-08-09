package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

final class InstallationRoot {
    private InstallationRoot() {
    }

    static Path resolve() throws IOException {
        CodeSource codeSource = MiniServer.class.getProtectionDomain().getCodeSource();

        if (codeSource != null) {
            URL locationUrl = codeSource.getLocation();
            if (locationUrl != null) {
                Path location = toPath(locationUrl);
                Path fileName = location.getFileName();
                if (Files.isRegularFile(location)
                        && fileName != null
                        && fileName.toString().toLowerCase().endsWith(".jar")) {
                    Path parent = location.getParent();
                    if (parent == null) {
                        throw new IOException("The Mini Server JAR has no installation directory: " + location);
                    }
                    return parent.toAbsolutePath().normalize();
                }
            }
        }

        String workingDirectory = System.getProperty("user.dir");
        if (workingDirectory == null || workingDirectory.trim().isEmpty()) {
            throw new IOException("The process working directory is not available");
        }
        return Paths.get(workingDirectory).toAbsolutePath().normalize();
    }

    private static Path toPath(URL locationUrl) throws IOException {
        try {
            return Paths.get(locationUrl.toURI()).toAbsolutePath().normalize();
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw new IOException("The Mini Server code location is invalid: " + locationUrl, exception);
        }
    }
}
