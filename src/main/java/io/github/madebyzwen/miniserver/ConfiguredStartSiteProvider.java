package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Applies Shared approval and optional Private filtering to current applications.
 */
final class ConfiguredStartSiteProvider implements StartSiteProvider {

    static final String START_SITES_FILE = "start-sites.txt";

    private static final String CONFIG_DIRECTORY = "config";
    private static final ConfigurationFileReader DEFAULT_FILE_READER =
            new ConfigurationFileReader() {
                @Override
                public Optional<List<String>> read(Path file) throws IOException {
                    try {
                        return Optional.of(Files.readAllLines(file, StandardCharsets.UTF_8));
                    } catch (NoSuchFileException exception) {
                        return Optional.empty();
                    }
                }
            };

    private final Path injectedWebRoot;
    private final Path injectedSharedConfiguration;
    private final Path injectedPrivateConfiguration;
    private final ConfigurationFileReader fileReader;

    ConfiguredStartSiteProvider() {
        this(null, null, null, DEFAULT_FILE_READER);
    }

    ConfiguredStartSiteProvider(
            Path webRoot,
            Path sharedConfiguration,
            Path privateConfiguration) {
        this(webRoot, sharedConfiguration, privateConfiguration, DEFAULT_FILE_READER);
    }

    ConfiguredStartSiteProvider(
            Path webRoot,
            Path sharedConfiguration,
            Path privateConfiguration,
            ConfigurationFileReader fileReader) {
        if (fileReader == null) {
            throw new NullPointerException("The configuration file reader must not be null.");
        }
        boolean allPathsInjected = webRoot != null
                && sharedConfiguration != null
                && privateConfiguration != null;
        boolean noPathsInjected = webRoot == null
                && sharedConfiguration == null
                && privateConfiguration == null;
        if (!allPathsInjected && !noPathsInjected) {
            throw new NullPointerException(
                    "Start-site paths must either all be injected or all be resolved.");
        }
        this.injectedWebRoot = webRoot;
        this.injectedSharedConfiguration = sharedConfiguration;
        this.injectedPrivateConfiguration = privateConfiguration;
        this.fileReader = fileReader;
    }

    @Override
    public List<String> loadStartSites() throws IOException {
        Path webRoot = resolveWebRoot();
        Path sharedConfiguration = resolveSharedConfiguration(webRoot);
        Optional<List<String>> sharedLines = fileReader.read(sharedConfiguration);
        if (!sharedLines.isPresent()) {
            return Collections.emptyList();
        }

        List<String> sharedSites = validateSharedSites(
                webRoot,
                parse(sharedLines.get()));
        Optional<List<String>> privateLines = fileReader.read(resolvePrivateConfiguration());
        if (!privateLines.isPresent()) {
            return sharedSites;
        }

        Set<String> privateSelection = new HashSet<String>(parse(privateLines.get()));
        List<String> effectiveSites = new ArrayList<String>();
        for (String sharedSite : sharedSites) {
            if (privateSelection.contains(sharedSite)) {
                effectiveSites.add(sharedSite);
            }
        }
        return Collections.unmodifiableList(effectiveSites);
    }

    private Path resolveWebRoot() throws IOException {
        if (injectedWebRoot != null) {
            return injectedWebRoot;
        }
        try {
            return WebRootResolver.resolve();
        } catch (StartupException exception) {
            throw new IOException("The installation web root cannot be resolved.", exception);
        }
    }

    private Path resolveSharedConfiguration(Path webRoot) throws IOException {
        if (injectedSharedConfiguration != null) {
            return injectedSharedConfiguration;
        }
        Path installationRoot = webRoot.toAbsolutePath().normalize().getParent();
        if (installationRoot == null) {
            throw new IOException("The Mini Server installation root cannot be resolved.");
        }
        return installationRoot.resolve(CONFIG_DIRECTORY).resolve(START_SITES_FILE);
    }

    private Path resolvePrivateConfiguration() throws IOException {
        if (injectedPrivateConfiguration != null) {
            return injectedPrivateConfiguration;
        }
        return UserConfigurationRootResolver.resolve().resolve(START_SITES_FILE);
    }

    private static List<String> parse(List<String> lines) {
        Set<String> entries = new LinkedHashSet<String>();
        for (String line : lines) {
            String entry = line.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            if (isSafeApplicationName(entry)) {
                entries.add(entry);
            }
        }
        return new ArrayList<String>(entries);
    }

    static boolean isSafeApplicationName(String entry) {
        if (entry == null
                || entry.isEmpty()
                || ".".equals(entry)
                || "..".equals(entry)
                || "_shared".equalsIgnoreCase(entry)
                || entry.indexOf('/') >= 0
                || entry.indexOf('\\') >= 0
                || entry.indexOf(':') >= 0
                || entry.indexOf('?') >= 0
                || entry.indexOf('#') >= 0) {
            return false;
        }
        for (int index = 0; index < entry.length(); index++) {
            if (Character.isISOControl(entry.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> validateSharedSites(
            Path configuredWebRoot,
            List<String> parsedSites) throws IOException {
        final Path webRoot;
        try {
            webRoot = configuredWebRoot.toRealPath();
        } catch (InvalidPathException | SecurityException exception) {
            throw new IOException("The Mini Server web root cannot be accessed.", exception);
        }

        List<String> validSites = new ArrayList<String>();
        for (String site : parsedSites) {
            if (isExistingDirectApplication(webRoot, site)) {
                validSites.add(site);
            }
        }
        return Collections.unmodifiableList(validSites);
    }

    private static boolean isExistingDirectApplication(Path webRoot, String site)
            throws IOException {
        final Path candidate;
        try {
            candidate = webRoot.resolve(site).normalize();
            if (!webRoot.equals(candidate.getParent())) {
                return false;
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    candidate,
                    BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                return false;
            }
            Path realApplication = candidate.toRealPath();
            return candidate.equals(realApplication)
                    && webRoot.equals(realApplication.getParent());
        } catch (NoSuchFileException | InvalidPathException exception) {
            return false;
        } catch (SecurityException exception) {
            throw new IOException("An application directory cannot be accessed.", exception);
        }
    }

    interface ConfigurationFileReader {

        Optional<List<String>> read(Path file) throws IOException;
    }
}
