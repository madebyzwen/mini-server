package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebRootResolverTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void packagedJarResolvesWebRootBesideJar() throws Exception {
        Path installationDirectory = temporaryDirectory.resolve("distribution");
        Files.createDirectories(installationDirectory);
        Path jarFile = Files.createFile(installationDirectory.resolve("mini-server.jar"));

        Path webRoot = WebRootResolver.resolve(
                jarFile.toUri().toURL(),
                temporaryDirectory.resolve("unrelated-working-directory"));

        assertEquals(installationDirectory.resolve("www"), webRoot);
    }

    @Test
    void explodedClassesUseWorkingDirectoryFallback() throws Exception {
        Path classesDirectory = temporaryDirectory.resolve("target/classes");
        Path workingDirectory = temporaryDirectory.resolve("project");
        Files.createDirectories(classesDirectory);

        Path webRoot = WebRootResolver.resolve(
                classesDirectory.toUri().toURL(),
                workingDirectory);

        assertEquals(workingDirectory.toAbsolutePath().resolve("www"), webRoot);
    }
}
