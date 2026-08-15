package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfiguredStartSiteProviderTest {

    @TempDir
    Path temporaryDirectory;

    private Path webRoot;
    private Path sharedConfiguration;
    private Path privateConfiguration;

    @BeforeEach
    void setUp() throws IOException {
        webRoot = temporaryDirectory.resolve("www");
        sharedConfiguration = temporaryDirectory.resolve("config/start-sites.txt");
        privateConfiguration = temporaryDirectory.resolve("user/config/start-sites.txt");
        Files.createDirectories(webRoot);
    }

    @Test
    void readsUtf8AndAppliesTrimmingCommentsValidationAndSharedDeduplication()
            throws Exception {
        createApplications("example", "two words", "café%");
        write(sharedConfiguration,
                "  # comment",
                "",
                "  example  ",
                "two words",
                "café%",
                "example",
                ".",
                "..",
                "_shared",
                "_ShArEd",
                "http://example.test/app",
                "host:8080",
                "name?query",
                "name#fragment",
                "/absolute",
                "C:\\absolute",
                "./relative",
                "../relative",
                "first/second",
                "first\\second",
                "control\u0001name");

        assertEquals(
                Arrays.asList("example", "two words", "café%"),
                provider().loadStartSites());
    }

    @Test
    void privateIsAnInclusionSetAndCannotReorderOrElevateShared() throws Exception {
        createApplications("example", "dashboard", "café", "notes", "development");
        write(sharedConfiguration, "example", "dashboard", "café", "notes");
        write(privateConfiguration, "notes", "café", "development", "example", "notes");

        assertEquals(
                Arrays.asList("example", "café", "notes"),
                provider().loadStartSites());
    }

    @Test
    void missingPrivateSelectsAllValidSharedWithoutCreatingPrivateFile() throws Exception {
        createApplications("first", "second");
        write(sharedConfiguration, "first", "second");

        assertEquals(Arrays.asList("first", "second"), provider().loadStartSites());
        assertFalse(Files.exists(privateConfiguration));
    }

    @Test
    void emptyAndEffectivelyEmptyPrivateSelectNothing() throws Exception {
        createApplications("first", "second");
        write(sharedConfiguration, "first", "second");

        write(privateConfiguration);
        assertTrue(provider().loadStartSites().isEmpty());

        write(privateConfiguration, "", " # only a comment ", "../invalid");
        assertTrue(provider().loadStartSites().isEmpty());
    }

    @Test
    void missingSharedSelectsNothingAndDoesNotEvaluateOrCreatePrivate() throws Exception {
        createApplications("private-only");
        write(privateConfiguration, "private-only");
        AtomicInteger reads = new AtomicInteger();
        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            reads.incrementAndGet();
            if (file.equals(sharedConfiguration)) {
                return Optional.empty();
            }
            throw new AssertionError("Private must not bypass missing Shared configuration.");
        };

        assertTrue(provider(reader).loadStartSites().isEmpty());
        assertEquals(1, reads.get());
        assertFalse(Files.exists(sharedConfiguration));
    }

    @Test
    void emptyAndEffectivelyEmptySharedSelectNothingWithoutPrivateBypass() throws Exception {
        createApplications("private-only");
        write(privateConfiguration, "private-only");

        write(sharedConfiguration);
        assertTrue(provider().loadStartSites().isEmpty());

        write(sharedConfiguration, "", " # comment", "_shared", "../invalid");
        assertTrue(provider().loadStartSites().isEmpty());
    }

    @Test
    void ignoresMissingApplicationsWithoutCreatingThemOrBlockingValidApplications()
            throws Exception {
        createApplications("existing");
        Path missing = webRoot.resolve("missing");
        write(sharedConfiguration, "missing", "existing", "another-missing");

        assertEquals(Collections.singletonList("existing"), provider().loadStartSites());
        assertFalse(Files.exists(missing));
        assertFalse(Files.exists(webRoot.resolve("another-missing")));
    }

    @Test
    void unreadableSharedFailsWithoutReadingPrivate() throws Exception {
        createApplications("first");
        AtomicInteger privateReads = new AtomicInteger();
        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            if (file.equals(sharedConfiguration)) {
                throw new IOException("deliberate unreadable Shared file");
            }
            privateReads.incrementAndGet();
            return Optional.of(Collections.singletonList("first"));
        };

        assertThrows(IOException.class, () -> provider(reader).loadStartSites());
        assertEquals(0, privateReads.get());
    }

    @Test
    void unreadablePrivateFailsWithoutFallingBackToShared() throws Exception {
        createApplications("first", "second");
        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            if (file.equals(sharedConfiguration)) {
                return Optional.of(Arrays.asList("first", "second"));
            }
            throw new IOException("deliberate unreadable Private file");
        };

        assertThrows(IOException.class, () -> provider(reader).loadStartSites());
    }

    @Test
    void rereadsSharedRemovalAndReadditionForAnExistingPrivateSelection()
            throws Exception {
        createApplications("first", "second");
        write(privateConfiguration, "second");

        write(sharedConfiguration, "first", "second");
        assertEquals(Collections.singletonList("second"), provider().loadStartSites());

        write(sharedConfiguration, "first");
        assertTrue(provider().loadStartSites().isEmpty());

        write(sharedConfiguration, "first", "second");
        assertEquals(Collections.singletonList("second"), provider().loadStartSites());
    }

    @Test
    void usersWithoutPrivateReceiveNewAndReaddedSharedApplications() throws Exception {
        createApplications("first", "second");

        write(sharedConfiguration, "first");
        assertEquals(Collections.singletonList("first"), provider().loadStartSites());

        write(sharedConfiguration, "first", "second");
        assertEquals(Arrays.asList("first", "second"), provider().loadStartSites());

        write(sharedConfiguration, "first");
        assertEquals(Collections.singletonList("first"), provider().loadStartSites());

        write(sharedConfiguration, "second", "first");
        assertEquals(Arrays.asList("second", "first"), provider().loadStartSites());
    }

    @Test
    void newlyAddedSharedApplicationDoesNotEnterExistingPrivateSelection() throws Exception {
        createApplications("first", "second");
        write(privateConfiguration, "first");

        write(sharedConfiguration, "first");
        assertEquals(Collections.singletonList("first"), provider().loadStartSites());

        write(sharedConfiguration, "first", "second");
        assertEquals(Collections.singletonList("first"), provider().loadStartSites());
    }

    @Test
    void symbolicLinkCannotQualifyAsAnApplicationDirectory() throws Exception {
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(outside);
        Path linkedApplication = webRoot.resolve("linked");
        try {
            Files.createSymbolicLink(linkedApplication, outside);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            return;
        }
        write(sharedConfiguration, "linked");

        assertTrue(provider().loadStartSites().isEmpty());
    }

    private ConfiguredStartSiteProvider provider() {
        return new ConfiguredStartSiteProvider(
                webRoot,
                sharedConfiguration,
                privateConfiguration);
    }

    private ConfiguredStartSiteProvider provider(
            ConfiguredStartSiteProvider.ConfigurationFileReader reader) {
        return new ConfiguredStartSiteProvider(
                webRoot,
                sharedConfiguration,
                privateConfiguration,
                reader);
    }

    private void createApplications(String... names) throws IOException {
        for (String name : names) {
            Files.createDirectories(webRoot.resolve(name));
        }
    }

    private static void write(Path file, String... lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, Arrays.asList(lines), StandardCharsets.UTF_8);
    }
}
