package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTargetResolverTest {

    @TempDir
    Path temporaryDirectory;

    private Path webRoot;
    private Path privateDataRoot;

    @BeforeEach
    void createRoots() throws IOException {
        webRoot = temporaryDirectory.resolve("www");
        privateDataRoot = temporaryDirectory.resolve("profile/MiniServerData");
        Files.createDirectories(webRoot);
    }

    @Test
    void resolvesSharedTargetForExistingSiteWithoutCreatingDataDirectory() throws Exception {
        Path site = createSite("example");

        ResolvedPersistenceTarget target = required(
                resolver().resolve("/example/api/shared/read"));

        assertEquals("example", target.getSite());
        assertEquals(PersistenceScope.SHARED, target.getScope());
        assertEquals("read", target.getOperation());
        assertEquals(site.toRealPath().resolve("data/data.json"), target.getDataFile());
        assertFalse(Files.exists(site.resolve("data")));
    }

    @Test
    void resolvesPrivateTargetBelowInjectedPrivateRoot() throws Exception {
        Path site = createSite("example");

        ResolvedPersistenceTarget target = required(
                resolver().resolve("/example/api/private/read"));

        assertEquals("example", target.getSite());
        assertEquals(PersistenceScope.PRIVATE, target.getScope());
        assertEquals("read", target.getOperation());
        assertEquals(
                privateDataRoot.toAbsolutePath().resolve("example/data/data.json"),
                target.getDataFile());
        assertFalse(Files.exists(site.resolve("data")));
        assertFalse(Files.exists(privateDataRoot));
    }

    @Test
    void twoSitesMapOnlyToTheirOwnTargets() throws Exception {
        Path example = createSite("example").toRealPath();
        Path dashboard = createSite("dashboard").toRealPath();
        PersistenceTargetResolver resolver = resolver();

        ResolvedPersistenceTarget exampleSharedTarget = required(
                resolver.resolve("/example/api/shared/read"));
        ResolvedPersistenceTarget dashboardSharedTarget = required(
                resolver.resolve("/dashboard/api/shared/read"));
        ResolvedPersistenceTarget examplePrivateTarget = required(
                resolver.resolve("/example/api/private/read"));
        ResolvedPersistenceTarget dashboardPrivateTarget = required(
                resolver.resolve("/dashboard/api/private/read"));

        assertEquals(example.resolve("data/data.json"), exampleSharedTarget.getDataFile());
        assertEquals(dashboard.resolve("data/data.json"), dashboardSharedTarget.getDataFile());
        assertEquals(
                privateDataRoot.resolve("example/data/data.json").toAbsolutePath(),
                examplePrivateTarget.getDataFile());
        assertEquals(
                privateDataRoot.resolve("dashboard/data/data.json").toAbsolutePath(),
                dashboardPrivateTarget.getDataFile());
    }

    @Test
    void onlyExplicitCanonicalScopesAreAccepted() throws Exception {
        createSite("example");
        PersistenceTargetResolver resolver = resolver();
        List<String> invalidPaths = Arrays.asList(
                "/example/api/read",
                "/example/api/readAll",
                "/example/api/default/read",
                "/example/api/user/read",
                "/example/api//read",
                "/example/api/SHARED/read",
                "/example/api/");

        for (String invalidPath : invalidPaths) {
            assertFalse(resolver.resolve(invalidPath).isPresent(), invalidPath);
        }

        assertArrayEquals(
                new PersistenceScope[]{PersistenceScope.SHARED, PersistenceScope.PRIVATE},
                PersistenceScope.values());
    }

    @Test
    void scopeFirstAndOtherNonCanonicalLayoutsAreRejected() throws Exception {
        createSite("example");
        PersistenceTargetResolver resolver = resolver();
        List<String> invalidPaths = Arrays.asList(
                "/example/shared/api/read",
                "/example/private/api/read",
                "/example/API/shared/read",
                "/api/example/shared/read",
                "/example/api/shared/read/extra");

        for (String invalidPath : invalidPaths) {
            assertFalse(resolver.resolve(invalidPath).isPresent(), invalidPath);
        }
    }

    @Test
    void unknownSiteDoesNotResolveOrCreateAnyDirectory() throws Exception {
        PersistenceTargetResolver resolver = resolver();

        assertFalse(resolver.resolve("/unknown/api/shared/read").isPresent());
        assertFalse(resolver.resolve("/unknown/api/private/read").isPresent());
        assertFalse(Files.exists(webRoot.resolve("unknown")));
        assertFalse(Files.exists(privateDataRoot.resolve("unknown")));
    }

    @Test
    void reservedSharedAreaIsNeverAPersistenceSiteRegardlessOfCase() throws Exception {
        createSite("_shared");
        createSite("_SHARED");
        PersistenceTargetResolver resolver = resolver();

        assertFalse(resolver.resolve("/_shared/api/shared/read").isPresent());
        assertFalse(resolver.resolve("/_SHARED/api/private/read").isPresent());
    }

    @Test
    void traversalAndPathManipulationNeverProduceATarget() throws Exception {
        createSite("example");
        createSite("other");
        PersistenceTargetResolver resolver = resolver();
        List<String> unsafePaths = Arrays.asList(
                "/../api/shared/read",
                "/%2e%2e/api/shared/read",
                "/example%2Fother/api/shared/read",
                "/example/api%2Fshared/read",
                "/example\\other/api/shared/read",
                "/example%5Cother/api/shared/read",
                "/C%3A/api/shared/read",
                "/example/api/shared/../read",
                "//example/api/shared/read",
                "/example/api/shared/read%00",
                "/example/%/shared/read",
                "/example/%2G/shared/read");

        for (String unsafePath : unsafePaths) {
            assertFalse(resolver.resolve(unsafePath).isPresent(), unsafePath);
        }
    }

    @Test
    void malformedUtf8FailsSafely() throws Exception {
        createSite("example");

        assertFalse(resolver().resolve("/%C3%28/api/shared/read").isPresent());
    }

    @Test
    void literalPlusRemainsPartOfSiteName() throws Exception {
        createSite("one+two");

        ResolvedPersistenceTarget target = required(
                resolver().resolve("/one+two/api/shared/read"));

        assertEquals("one+two", target.getSite());
    }

    @Test
    void percentEncodedSpaceAndUnicodeSiteNamesResolveOnce() throws Exception {
        createSite("space site");
        createSite("café");
        PersistenceTargetResolver resolver = resolver();

        ResolvedPersistenceTarget spaced = required(
                resolver.resolve("/space%20site/api/shared/read"));
        ResolvedPersistenceTarget unicode = required(
                resolver.resolve("/caf%C3%A9/api/private/read"));

        assertEquals("space site", spaced.getSite());
        assertEquals("café", unicode.getSite());
    }

    @Test
    void symbolicLinkSiteCannotEscapeWebRootWhenSupported() throws Exception {
        Path outsideSite = temporaryDirectory.resolve("outside-site");
        Files.createDirectories(outsideSite);
        Files.write(
                outsideSite.resolve("recognizable.txt"),
                "outside".getBytes(StandardCharsets.UTF_8));
        createSiteAlias("linked-site", outsideSite);

        assertFalse(resolver().resolve("/linked-site/api/shared/read").isPresent());
    }

    @Test
    void symbolicLinkToReservedSharedAreaIsNotAPersistenceSite() throws Exception {
        Path reservedSharedArea = createSite("_shared");
        createSiteAlias("alias", reservedSharedArea);
        PersistenceTargetResolver resolver = resolver();

        assertFalse(resolver.resolve("/alias/api/shared/read").isPresent());
        assertFalse(resolver.resolve("/alias/api/private/read").isPresent());
    }

    @Test
    void symbolicLinkToAnotherApplicationIsNotAPersistenceSite() throws Exception {
        Path dashboard = createSite("dashboard");
        createSiteAlias("alias", dashboard);
        PersistenceTargetResolver resolver = resolver();

        assertFalse(resolver.resolve("/alias/api/shared/read").isPresent());
        assertFalse(resolver.resolve("/alias/api/private/read").isPresent());
    }

    @Test
    void sharedResolutionDoesNotConsultPrivateRootProvider() throws Exception {
        createSite("example");
        PersistenceTargetResolver resolver = new PersistenceTargetResolver(
                webRoot,
                new PersistenceTargetResolver.PrivateDataRootProvider() {
                    @Override
                    public Path resolve() {
                        throw new AssertionError("Shared mapping consulted the private root.");
                    }
                });

        assertTrue(resolver.resolve("/example/api/shared/read").isPresent());
    }

    @Test
    void privateResolutionClearlyFailsWhenPrivateRootIsUnavailable() throws Exception {
        createSite("example");
        PersistenceTargetResolver resolver = new PersistenceTargetResolver(
                webRoot,
                new PersistenceTargetResolver.PrivateDataRootProvider() {
                    @Override
                    public Path resolve() throws IOException {
                        throw new IOException("APPDATA unavailable for test");
                    }
                });

        IOException exception = assertThrows(
                IOException.class,
                () -> resolver.resolve("/example/api/private/read"));

        assertEquals("APPDATA unavailable for test", exception.getMessage());
    }

    @Test
    void unknownOperationRemainsAnUninterpretedStructuralComponent() throws Exception {
        createSite("example");

        ResolvedPersistenceTarget target = required(
                resolver().resolve("/example/api/shared/futureOperation"));

        assertEquals("futureOperation", target.getOperation());
        assertEquals(PersistenceScope.SHARED, target.getScope());
        assertFalse(Files.exists(target.getDataFile()));
    }

    @Test
    void queryLikeOrFilesystemOverrideTextCannotRedirectTarget() throws Exception {
        Path site = createSite("example").toRealPath();
        PersistenceTargetResolver resolver = resolver();

        assertFalse(resolver.resolve(
                "/example/api/shared/read?target=/outside/data.json").isPresent());

        ResolvedPersistenceTarget target = required(
                resolver.resolve("/example/api/shared/read"));
        assertEquals(site.resolve("data/data.json"), target.getDataFile());
    }

    @Test
    void regularFileCannotActAsApplicationSite() throws Exception {
        Files.write(webRoot.resolve("not-a-site"), new byte[]{1});

        assertFalse(resolver().resolve("/not-a-site/api/shared/read").isPresent());
    }

    private Path createSite(String site) throws IOException {
        Path siteDirectory = webRoot.resolve(site);
        Files.createDirectories(siteDirectory);
        return siteDirectory;
    }

    private Path createSiteAlias(String alias, Path target) {
        Path siteLink = webRoot.resolve(alias);
        try {
            return Files.createSymbolicLink(siteLink, target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(
                    false,
                    "Symbolic links are unavailable in this test environment.");
            return siteLink;
        }
    }

    private PersistenceTargetResolver resolver() throws IOException {
        return new PersistenceTargetResolver(webRoot, privateDataRoot);
    }

    private static ResolvedPersistenceTarget required(
            Optional<ResolvedPersistenceTarget> target) {
        assertTrue(target.isPresent());
        return target.get();
    }
}
