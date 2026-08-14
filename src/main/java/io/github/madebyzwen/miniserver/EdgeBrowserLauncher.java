package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Launches the normally installed Microsoft Edge executable on Windows.
 */
final class EdgeBrowserLauncher implements BrowserLauncher {

    private static final String EDGE_RELATIVE_PATH = "Microsoft/Edge/Application/msedge.exe";
    private static final String[] INSTALL_ROOT_VARIABLES = {
        "ProgramFiles(x86)",
        "ProgramFiles",
        "LOCALAPPDATA"
    };

    private final Map<String, String> environment;
    private final ProcessStarter processStarter;

    EdgeBrowserLauncher() {
        this(System.getenv(), new ProcessStarter() {
            @Override
            public void start(Path executable, String url) throws IOException {
                new ProcessBuilder(executable.toString(), url)
                        .inheritIO()
                        .start();
            }
        });
    }

    EdgeBrowserLauncher(Map<String, String> environment, ProcessStarter processStarter) {
        if (environment == null || processStarter == null) {
            throw new NullPointerException("Edge launcher dependencies must not be null.");
        }
        this.environment = environment;
        this.processStarter = processStarter;
    }

    @Override
    public void open(String url) throws IOException {
        processStarter.start(findEdgeExecutable(), url);
    }

    private Path findEdgeExecutable() throws IOException {
        for (String variable : INSTALL_ROOT_VARIABLES) {
            String installRoot = environment.get(variable);
            if (installRoot == null || installRoot.trim().isEmpty()) {
                continue;
            }

            try {
                Path candidate = Paths.get(installRoot).resolve(EDGE_RELATIVE_PATH);
                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            } catch (InvalidPathException ignored) {
                // Continue to the remaining normal Windows installation locations.
            }
        }

        throw new IOException("The Microsoft Edge executable was not found.");
    }

    interface ProcessStarter {
        void start(Path executable, String url) throws IOException;
    }
}
