package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EdgeBrowserLauncherTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void launchesEdgeDirectlyFromANormalWindowsInstallLocation() throws Exception {
        Path programFiles = temporaryDirectory.resolve("Program Files (x86)");
        Path edgeExecutable = programFiles.resolve("Microsoft/Edge/Application/msedge.exe");
        Files.createDirectories(edgeExecutable.getParent());
        Files.createFile(edgeExecutable);

        Map<String, String> environment = new HashMap<String, String>();
        environment.put("ProgramFiles(x86)", programFiles.toString());
        RecordingProcessStarter processStarter = new RecordingProcessStarter();
        EdgeBrowserLauncher launcher = new EdgeBrowserLauncher(environment, processStarter);

        launcher.open("http://127.0.0.1:51847/example/");

        assertEquals(edgeExecutable, processStarter.executable);
        assertEquals("http://127.0.0.1:51847/example/", processStarter.url);
    }

    @Test
    void reportsFailureWhenEdgeIsNotInstalledInANormalLocation() {
        RecordingProcessStarter processStarter = new RecordingProcessStarter();
        EdgeBrowserLauncher launcher = new EdgeBrowserLauncher(
                Collections.<String, String>emptyMap(),
                processStarter);

        assertThrows(
                IOException.class,
                () -> launcher.open("http://127.0.0.1:51847/example/"));
        assertFalse(processStarter.started);
    }

    private static final class RecordingProcessStarter
            implements EdgeBrowserLauncher.ProcessStarter {

        private boolean started;
        private Path executable;
        private String url;

        @Override
        public void start(Path executable, String url) {
            this.started = true;
            this.executable = executable;
            this.url = url;
        }
    }
}
