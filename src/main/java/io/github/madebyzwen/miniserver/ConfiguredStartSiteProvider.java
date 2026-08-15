package io.github.madebyzwen.miniserver;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/** Owns Shared approval, current-user selection, and safe selection updates. */
final class ConfiguredStartSiteProvider implements StartSiteProvider {

    static final String START_SITES_FILE = "start-sites.txt";

    private static final String CONFIG_DIRECTORY = "config";
    private static final String LOCK_SUFFIX = ".lock";
    private static final long LOCK_TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(1L);
    private static final long LOCK_RETRY_NANOS = TimeUnit.MILLISECONDS.toNanos(10L);
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

    ConfiguredStartSiteProvider(Path webRoot, Path sharedConfiguration, Path privateConfiguration) {
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
    public StartSitePlan planStartSites() throws IOException {
        final PrivateSelection privateSelection;
        try {
            privateSelection = loadPrivateSelection();
        } catch (IOException | RuntimeException exception) {
            return StartSitePlan.root(
                    "The current-user start-site selection could not be read.");
        }

        final SharedStartSites shared;
        try {
            shared = loadSharedStartSites();
        } catch (IOException exception) {
            return StartSitePlan.root("Shared start-site approval is unavailable.");
        }
        if (!shared.isAvailable()) {
            return StartSitePlan.root("Shared start-site approval is unavailable.");
        }

        if (privateSelection.getState() == PrivateSelectionState.MISSING) {
            return StartSitePlan.root(null);
        }
        List<String> effective = effectiveSites(
                shared.getSites(), privateSelection.getLines());
        return effective.isEmpty()
                ? StartSitePlan.root(null)
                : StartSitePlan.applications(effective);
    }

    /** Compatibility view retained for focused parser and filtering tests. */
    List<String> loadStartSites() throws IOException {
        SharedStartSites shared = loadSharedStartSites();
        if (!shared.isAvailable()) {
            return Collections.emptyList();
        }
        PrivateSelection privateSelection = loadPrivateSelection();
        if (privateSelection.getState() == PrivateSelectionState.MISSING) {
            return shared.getSites();
        }
        return effectiveSites(shared.getSites(), privateSelection.getLines());
    }

    RootPageState loadRootPageState() {
        final PrivateSelection privateSelection;
        try {
            privateSelection = loadPrivateSelection();
        } catch (IOException | RuntimeException exception) {
            return rootPageState(
                    loadSharedStartSitesForPage(),
                    PrivateSelection.unreadable());
        }
        return rootPageState(loadSharedStartSitesForPage(), privateSelection);
    }

    SharedStartSites loadSharedStartSites() throws IOException {
        Path webRoot = resolveWebRoot();
        Optional<List<String>> sharedLines = fileReader.read(resolveSharedConfiguration(webRoot));
        if (!sharedLines.isPresent()) {
            return SharedStartSites.unavailable();
        }
        return SharedStartSites.available(validateSharedSites(webRoot, parse(sharedLines.get())));
    }

    List<String> saveSelection(List<String> requestedSites) throws IOException {
        if (requestedSites == null) {
            throw new NullPointerException("The requested sites must not be null.");
        }
        if (requestedSites.isEmpty()) {
            throw new EmptySelectionException();
        }
        final SharedStartSites shared;
        try {
            shared = loadSharedStartSites();
        } catch (IOException exception) {
            throw new SharedConfigurationUnavailableException(exception);
        }
        if (!shared.isAvailable()) {
            throw new SharedConfigurationUnavailableException();
        }
        Set<String> requested = new HashSet<String>();
        for (String site : requestedSites) {
            if (isSafeApplicationName(site)) {
                requested.add(site);
            }
        }
        List<String> normalized = new ArrayList<String>();
        for (String approved : shared.getSites()) {
            if (requested.contains(approved)) {
                normalized.add(approved);
            }
        }
        if (normalized.isEmpty()) {
            throw new SelectionConflictException();
        }
        replacePrivateSelection(resolvePrivateConfiguration(), normalized);
        return Collections.unmodifiableList(normalized);
    }

    private SharedStartSites loadSharedStartSitesForPage() {
        try {
            return loadSharedStartSites();
        } catch (IOException | RuntimeException exception) {
            return SharedStartSites.unavailable();
        }
    }

    private static RootPageState rootPageState(
            SharedStartSites shared,
            PrivateSelection privateSelection) {
        List<String> selected = Collections.emptyList();
        if (shared.isAvailable()) {
            if (privateSelection.getState() == PrivateSelectionState.MISSING) {
                selected = shared.getSites();
            } else if (privateSelection.getState() == PrivateSelectionState.READABLE) {
                selected = effectiveSites(shared.getSites(), privateSelection.getLines());
            }
        }
        return new RootPageState(shared, privateSelection.getState(), selected);
    }

    private PrivateSelection loadPrivateSelection() throws IOException {
        Optional<List<String>> lines = fileReader.read(resolvePrivateConfiguration());
        return lines.isPresent()
                ? PrivateSelection.readable(lines.get())
                : PrivateSelection.missing();
    }

    private static List<String> effectiveSites(
            List<String> sharedSites,
            List<String> privateLines) {
        Set<String> privateSelection = new HashSet<String>(parse(privateLines));
        List<String> effectiveSites = new ArrayList<String>();
        for (String sharedSite : sharedSites) {
            if (privateSelection.contains(sharedSite)) {
                effectiveSites.add(sharedSite);
            }
        }
        return Collections.unmodifiableList(effectiveSites);
    }

    private void replacePrivateSelection(final Path file, final List<String> sites)
            throws IOException {
        withPrivateConfigurationLock(file, new LockedConfigurationOperation<Void>() {
            @Override
            public Void run() throws IOException {
                writeConfigurationAtomically(file, sites);
                return null;
            }
        });
    }

    private <T> T withPrivateConfigurationLock(
            Path file,
            LockedConfigurationOperation<T> operation) throws IOException {
        preparePrivateConfigurationDirectory(file);
        Path lockFile = file.resolveSibling(file.getFileName().toString() + LOCK_SUFFIX);
        rejectLink(lockFile);
        try (FileChannel channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE);
             FileLock ignored = acquireLock(channel)) {
            rejectLink(file);
            return operation.run();
        }
    }

    private static void preparePrivateConfigurationDirectory(Path file) throws IOException {
        Path directory = file.getParent();
        Path miniServerDirectory = directory == null ? null : directory.getParent();
        if (directory == null || miniServerDirectory == null) {
            throw new IOException("The current-user configuration path is invalid.");
        }
        createDirectoriesWithoutFollowingLinks(directory);
        requireDirectory(miniServerDirectory);
    }

    private static void writeConfigurationAtomically(
            Path file,
            List<String> sites) throws IOException {
        Path temporary = null;
        boolean moved = false;
        try {
            temporary = Files.createTempFile(file.getParent(), "start-sites-", ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(
                    temporary,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                for (String site : sites) {
                    writer.write(site);
                    writer.newLine();
                }
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            moved = true;
        } finally {
            if (!moved && temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | SecurityException ignored) {
                    // Preserve the original configuration write failure.
                }
            }
        }
    }

    private static FileLock acquireLock(FileChannel channel) throws IOException {
        long started = System.nanoTime();
        while (true) {
            try {
                FileLock lock = channel.tryLock();
                if (lock != null) {
                    return lock;
                }
            } catch (OverlappingFileLockException exception) {
                // Another thread in this JVM owns this configuration lock.
            }
            long elapsed = System.nanoTime() - started;
            if (elapsed >= LOCK_TIMEOUT_NANOS) {
                throw new IOException("Timed out waiting for the start-site configuration lock.");
            }
            LockSupport.parkNanos(Math.min(LOCK_RETRY_NANOS, LOCK_TIMEOUT_NANOS - elapsed));
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                throw new IOException("Start-site configuration locking was interrupted.");
            }
        }
    }

    private static void requireDirectory(Path directory) throws IOException {
        BasicFileAttributes attributes = Files.readAttributes(
                directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("The current-user configuration path is unsafe.");
        }
    }

    private static void createDirectoriesWithoutFollowingLinks(Path directory)
            throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            requireDirectory(directory);
            return;
        }
        Path parent = directory.getParent();
        if (parent == null) {
            throw new IOException("The current-user configuration path is invalid.");
        }
        createDirectoriesWithoutFollowingLinks(parent);
        try {
            Files.createDirectory(directory);
        } catch (java.nio.file.FileAlreadyExistsException exception) {
            // A concurrent initializer may have created this directory.
        }
        requireDirectory(directory);
    }

    private static void rejectLink(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            throw new IOException("The current-user configuration path is unsafe.");
        }
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
        return injectedPrivateConfiguration != null
                ? injectedPrivateConfiguration
                : UserConfigurationRootResolver.resolve().resolve(START_SITES_FILE);
    }

    private static List<String> parse(List<String> lines) {
        Set<String> entries = new LinkedHashSet<String>();
        for (String line : lines) {
            String entry = line.trim();
            if (!entry.isEmpty() && !entry.startsWith("#") && isSafeApplicationName(entry)) {
                entries.add(entry);
            }
        }
        return new ArrayList<String>(entries);
    }

    static boolean isSafeApplicationName(String entry) {
        if (entry == null || entry.isEmpty() || ".".equals(entry) || "..".equals(entry)
                || "_shared".equalsIgnoreCase(entry) || entry.indexOf('/') >= 0
                || entry.indexOf('\\') >= 0 || entry.indexOf(':') >= 0
                || entry.indexOf('?') >= 0 || entry.indexOf('#') >= 0) {
            return false;
        }
        for (int index = 0; index < entry.length(); index++) {
            if (Character.isISOControl(entry.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static List<String> validateSharedSites(Path configuredWebRoot, List<String> parsedSites)
            throws IOException {
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
        try {
            Path candidate = webRoot.resolve(site).normalize();
            if (!webRoot.equals(candidate.getParent())) {
                return false;
            }
            BasicFileAttributes attributes = Files.readAttributes(
                    candidate, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
                return false;
            }
            Path realApplication = candidate.toRealPath();
            return candidate.equals(realApplication) && webRoot.equals(realApplication.getParent());
        } catch (NoSuchFileException | InvalidPathException exception) {
            return false;
        } catch (SecurityException exception) {
            throw new IOException("An application directory cannot be accessed.", exception);
        }
    }

    static final class SharedStartSites {
        private final boolean available;
        private final List<String> sites;

        private SharedStartSites(boolean available, List<String> sites) {
            this.available = available;
            this.sites = Collections.unmodifiableList(new ArrayList<String>(sites));
        }

        static SharedStartSites available(List<String> sites) {
            return new SharedStartSites(true, sites);
        }

        static SharedStartSites unavailable() {
            return new SharedStartSites(false, Collections.<String>emptyList());
        }

        boolean isAvailable() { return available; }
        List<String> getSites() { return sites; }
    }

    enum PrivateSelectionState { MISSING, READABLE, UNREADABLE }

    static final class RootPageState {
        private final SharedStartSites shared;
        private final PrivateSelectionState privateState;
        private final List<String> selectedSites;

        private RootPageState(
                SharedStartSites shared,
                PrivateSelectionState privateState,
                List<String> selectedSites) {
            this.shared = shared;
            this.privateState = privateState;
            this.selectedSites = Collections.unmodifiableList(
                    new ArrayList<String>(selectedSites));
        }

        SharedStartSites getShared() { return shared; }
        PrivateSelectionState getPrivateState() { return privateState; }
        List<String> getSelectedSites() { return selectedSites; }
        boolean isSavingAvailable() {
            return shared.isAvailable() && !shared.getSites().isEmpty();
        }
    }

    private static final class PrivateSelection {
        private final PrivateSelectionState state;
        private final List<String> lines;

        private PrivateSelection(PrivateSelectionState state, List<String> lines) {
            this.state = state;
            this.lines = Collections.unmodifiableList(new ArrayList<String>(lines));
        }

        static PrivateSelection missing() {
            return new PrivateSelection(
                    PrivateSelectionState.MISSING,
                    Collections.<String>emptyList());
        }

        static PrivateSelection readable(List<String> lines) {
            return new PrivateSelection(PrivateSelectionState.READABLE, lines);
        }

        static PrivateSelection unreadable() {
            return new PrivateSelection(
                    PrivateSelectionState.UNREADABLE,
                    Collections.<String>emptyList());
        }

        PrivateSelectionState getState() { return state; }
        List<String> getLines() { return lines; }
    }

    static final class EmptySelectionException extends IOException {
        EmptySelectionException() {
            super("At least one application must be selected.");
        }
    }

    static final class SelectionConflictException extends IOException {
        SelectionConflictException() {
            super("The requested selection is no longer available.");
        }
    }

    static final class SharedConfigurationUnavailableException extends IOException {
        SharedConfigurationUnavailableException() {
            super("Shared start-site approval is unavailable.");
        }

        SharedConfigurationUnavailableException(Throwable cause) {
            super("Shared start-site approval is unavailable.", cause);
        }
    }

    interface ConfigurationFileReader {
        Optional<List<String>> read(Path file) throws IOException;
    }

    private interface LockedConfigurationOperation<T> {
        T run() throws IOException;
    }
}
