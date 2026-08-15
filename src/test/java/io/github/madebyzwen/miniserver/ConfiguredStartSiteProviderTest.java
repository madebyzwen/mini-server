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

    @Test
    void missingPrivatePlansRootWithoutCreatingPrivateConfiguration() throws Exception {
        createApplications("first", "second");
        write(sharedConfiguration, "first", "second");

        StartSitePlan plan = provider().planStartSites();

        assertEquals(StartSitePlan.Kind.ROOT, plan.getKind());
        assertTrue(plan.getSites().isEmpty());
        assertFalse(Files.exists(privateConfiguration));
    }

    @Test
    void firstRunRemainsUncommittedUntilSaveThenPlansSavedApplications()
            throws Exception {
        createApplications("first", "second");
        write(sharedConfiguration, "first", "second");
        ConfiguredStartSiteProvider provider = provider();

        assertEquals(StartSitePlan.Kind.ROOT, provider.planStartSites().getKind());
        assertFalse(Files.exists(privateConfiguration));
        assertEquals(StartSitePlan.Kind.ROOT, provider.planStartSites().getKind());
        assertFalse(Files.exists(privateConfiguration));

        assertEquals(Arrays.asList("first", "second"),
                provider.saveSelection(Arrays.asList("second", "first")));
        assertEquals(Arrays.asList("first", "second"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
        StartSitePlan saved = provider.planStartSites();
        assertEquals(StartSitePlan.Kind.APPLICATIONS, saved.getKind());
        assertEquals(Arrays.asList("first", "second"), saved.getSites());
    }

    @Test
    void readableEmptySharedPlansRootWithoutCreatingPrivateConfiguration() throws Exception {
        write(sharedConfiguration);

        StartSitePlan plan = provider().planStartSites();

        assertEquals(StartSitePlan.Kind.ROOT, plan.getKind());
        assertFalse(Files.exists(privateConfiguration));
    }

    @Test
    void unavailableSharedAlwaysPlansRecoveryRoot() throws Exception {
        StartSitePlan first = provider().planStartSites();
        assertEquals(StartSitePlan.Kind.ROOT, first.getKind());
        assertFalse(Files.exists(privateConfiguration));

        write(privateConfiguration, "first");
        StartSitePlan existing = provider().planStartSites();
        assertEquals(StartSitePlan.Kind.ROOT, existing.getKind());
        assertTrue(existing.getDiagnostic().contains("unavailable"));
    }

    @Test
    void existingPrivatePlansOnlyCurrentSharedIntersection() throws Exception {
        createApplications("first", "second", "third");
        write(sharedConfiguration, "first", "second", "third");
        write(privateConfiguration, "third", "first", "unapproved");

        StartSitePlan plan = provider().planStartSites();

        assertEquals(StartSitePlan.Kind.APPLICATIONS, plan.getKind());
        assertEquals(Arrays.asList("first", "third"), plan.getSites());
    }

    @Test
    void saveReplacesSelectionWithSharedOrderedApprovedDeduplicatedEntries()
            throws Exception {
        createApplications("first", "second", "third");
        write(sharedConfiguration, "first", "second", "third");
        write(privateConfiguration, "old");

        assertEquals(
                Arrays.asList("first", "third"),
                provider().saveSelection(Arrays.asList(
                        "third", "unknown", "first", "third", "../unsafe")));
        assertEquals(
                Arrays.asList("first", "third"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));

        assertThrows(
                ConfiguredStartSiteProvider.EmptySelectionException.class,
                () -> provider().saveSelection(Collections.<String>emptyList()));
        assertEquals(
                Arrays.asList("first", "third"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
    }

    @Test
    void saveRejectsUnavailableSharedAndPreservesPrivateSelection() throws Exception {
        write(privateConfiguration, "existing");

        assertThrows(
                ConfiguredStartSiteProvider.SharedConfigurationUnavailableException.class,
                () -> provider().saveSelection(Collections.singletonList("replacement")));
        assertEquals(
                Collections.singletonList("existing"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
    }

    @Test
    void saveRejectsUnreadableSharedAndPreservesPrivateSelection() throws Exception {
        write(privateConfiguration, "existing");
        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            if (file.equals(sharedConfiguration)) {
                throw new IOException("deliberately unreadable Shared file");
            }
            return Optional.of(Collections.singletonList("existing"));
        };

        assertThrows(
                ConfiguredStartSiteProvider.SharedConfigurationUnavailableException.class,
                () -> provider(reader).saveSelection(Collections.singletonList("replacement")));
        assertEquals(Collections.singletonList("existing"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
    }

    @Test
    void unreadableSharedWithMissingPrivatePlansRootWithoutCreatingASelection()
            throws Exception {
        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            if (file.equals(privateConfiguration)) {
                return Optional.empty();
            }
            throw new IOException("deliberate unreadable Shared file");
        };

        StartSitePlan plan = provider(reader).planStartSites();

        assertEquals(StartSitePlan.Kind.ROOT, plan.getKind());
        assertFalse(Files.exists(privateConfiguration));
    }

    @Test
    void unreadableExistingPrivatePlansRecoveryRootWithoutFallingBackToShared()
            throws Exception {
        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            if (file.equals(privateConfiguration)) {
                throw new IOException("deliberate unreadable Private file");
            }
            return Optional.of(Collections.singletonList("first"));
        };

        StartSitePlan plan = provider(reader).planStartSites();

        assertEquals(StartSitePlan.Kind.ROOT, plan.getKind());
        assertTrue(plan.getDiagnostic().contains("could not be read"));
    }

    @Test
    void zeroEffectivePrivateVariantsPlanRecoveryRoot() throws Exception {
        createApplications("first", "second");
        write(sharedConfiguration, "first", "second");

        write(privateConfiguration);
        assertEquals(StartSitePlan.Kind.ROOT, provider().planStartSites().getKind());

        write(privateConfiguration, "# comment", "../invalid");
        assertEquals(StartSitePlan.Kind.ROOT, provider().planStartSites().getKind());

        write(privateConfiguration, "removed-application");
        assertEquals(StartSitePlan.Kind.ROOT, provider().planStartSites().getKind());
    }

    @Test
    void rootPageStateDistinguishesMissingReadableAndUnreadablePrivate() throws Exception {
        createApplications("first", "second");
        write(sharedConfiguration, "first", "second");

        ConfiguredStartSiteProvider.RootPageState missing = provider().loadRootPageState();
        assertEquals(ConfiguredStartSiteProvider.PrivateSelectionState.MISSING,
                missing.getPrivateState());
        assertEquals(Arrays.asList("first", "second"), missing.getSelectedSites());
        assertTrue(missing.isSavingAvailable());
        assertFalse(Files.exists(privateConfiguration));

        write(privateConfiguration, "second", "stale");
        ConfiguredStartSiteProvider.RootPageState readable = provider().loadRootPageState();
        assertEquals(ConfiguredStartSiteProvider.PrivateSelectionState.READABLE,
                readable.getPrivateState());
        assertEquals(Collections.singletonList("second"), readable.getSelectedSites());

        ConfiguredStartSiteProvider.ConfigurationFileReader reader = file -> {
            if (file.equals(privateConfiguration)) {
                throw new IOException("deliberately unreadable Private file");
            }
            return Optional.of(Arrays.asList("first", "second"));
        };
        ConfiguredStartSiteProvider.RootPageState unreadable = provider(reader).loadRootPageState();
        assertEquals(ConfiguredStartSiteProvider.PrivateSelectionState.UNREADABLE,
                unreadable.getPrivateState());
        assertTrue(unreadable.getSelectedSites().isEmpty());
        assertTrue(unreadable.isSavingAvailable());
    }

    @Test
    void rootPageStateMakesSavingUnavailableForEmptyOrUnavailableShared() throws Exception {
        write(sharedConfiguration);
        ConfiguredStartSiteProvider.RootPageState empty = provider().loadRootPageState();
        assertTrue(empty.getShared().isAvailable());
        assertFalse(empty.isSavingAvailable());

        Files.delete(sharedConfiguration);
        ConfiguredStartSiteProvider.RootPageState unavailable = provider().loadRootPageState();
        assertFalse(unavailable.getShared().isAvailable());
        assertFalse(unavailable.isSavingAvailable());
    }

    @Test
    void saveRejectsNormalizedZeroAndPreservesPrivateSelection() throws Exception {
        createApplications("first");
        write(sharedConfiguration, "first");
        write(privateConfiguration, "first");

        assertThrows(
                ConfiguredStartSiteProvider.SelectionConflictException.class,
                () -> provider().saveSelection(Arrays.asList("stale", "../unsafe")));
        assertEquals(Collections.singletonList("first"),
                Files.readAllLines(privateConfiguration, StandardCharsets.UTF_8));
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
