package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;

final class PersistenceTargetResolver {

    private static final String API_COMPONENT = "api";
    private static final String RESERVED_SHARED_SITE = "_shared";
    private static final String DATA_DIRECTORY = "data";
    private static final String DATA_FILE = "data.json";

    private static final PrivateDataRootProvider PRODUCTION_PRIVATE_ROOT =
            new PrivateDataRootProvider() {
                @Override
                public Path resolve() throws IOException {
                    return PrivateDataRootResolver.resolve();
                }
            };

    private final Path webRoot;
    private final PrivateDataRootProvider privateDataRootProvider;

    PersistenceTargetResolver(Path webRoot) throws IOException {
        this(webRoot, PRODUCTION_PRIVATE_ROOT);
    }

    PersistenceTargetResolver(Path webRoot, Path privateDataRoot) throws IOException {
        this(webRoot, fixedPrivateRoot(privateDataRoot));
    }

    PersistenceTargetResolver(
            Path webRoot,
            PrivateDataRootProvider privateDataRootProvider) throws IOException {
        if (!Files.isDirectory(webRoot) || !Files.isReadable(webRoot)) {
            throw new IOException("The Mini Server web root is not an accessible directory.");
        }
        this.webRoot = webRoot.toRealPath();
        this.privateDataRootProvider = privateDataRootProvider;
    }

    Optional<ResolvedPersistenceTarget> resolve(String rawPath) throws IOException {
        String decodedPath = UrlPathDecoder.decode(rawPath);
        if (UrlPathDecoder.containsEncodedPathSeparator(rawPath)
                || !isSafePath(decodedPath)) {
            return Optional.empty();
        }

        String[] components = decodedPath.substring(1).split("/", -1);
        if (components.length != 4
                || hasEmptyOrRelativeComponent(components)
                || !API_COMPONENT.equals(components[1])
                || RESERVED_SHARED_SITE.equalsIgnoreCase(components[0])) {
            return Optional.empty();
        }

        PersistenceScope scope = PersistenceScope.fromPathComponent(components[2]);
        if (scope == null) {
            return Optional.empty();
        }

        Path siteDirectory;
        try {
            Path candidate = webRoot.resolve(components[0]).normalize();
            if (!candidate.startsWith(webRoot)) {
                return Optional.empty();
            }
            siteDirectory = candidate.toRealPath();
        } catch (NoSuchFileException | InvalidPathException exception) {
            return Optional.empty();
        } catch (SecurityException exception) {
            throw new IOException("The application site cannot be accessed.", exception);
        }

        BasicFileAttributes siteAttributes =
                Files.readAttributes(siteDirectory, BasicFileAttributes.class);
        if (!siteDirectory.startsWith(webRoot) || !siteAttributes.isDirectory()) {
            return Optional.empty();
        }

        Path dataFile;
        if (scope == PersistenceScope.SHARED) {
            dataFile = siteDirectory.resolve(DATA_DIRECTORY).resolve(DATA_FILE).normalize();
            if (!dataFile.startsWith(webRoot)) {
                return Optional.empty();
            }
        } else {
            Path privateDataRoot = resolvePrivateDataRoot();
            dataFile = privateDataRoot
                    .resolve(components[0])
                    .resolve(DATA_DIRECTORY)
                    .resolve(DATA_FILE)
                    .normalize();
            if (!dataFile.startsWith(privateDataRoot)) {
                return Optional.empty();
            }
        }

        return Optional.of(new ResolvedPersistenceTarget(
                components[0],
                scope,
                components[3],
                dataFile));
    }

    private Path resolvePrivateDataRoot() throws IOException {
        Path privateDataRoot = privateDataRootProvider.resolve();
        if (privateDataRoot == null) {
            throw new IOException("The private data root is unavailable.");
        }
        return privateDataRoot.toAbsolutePath().normalize();
    }

    private static boolean isSafePath(String decodedPath) {
        return decodedPath != null
                && decodedPath.startsWith("/")
                && !decodedPath.startsWith("//")
                && decodedPath.indexOf('\\') < 0
                && decodedPath.indexOf(':') < 0
                && decodedPath.indexOf('?') < 0
                && decodedPath.indexOf('#') < 0
                && !UrlPathDecoder.containsControlCharacter(decodedPath);
    }

    private static boolean hasEmptyOrRelativeComponent(String[] components) {
        for (String component : components) {
            if (component.isEmpty() || ".".equals(component) || "..".equals(component)) {
                return true;
            }
        }
        return false;
    }

    private static PrivateDataRootProvider fixedPrivateRoot(final Path privateDataRoot) {
        return new PrivateDataRootProvider() {
            @Override
            public Path resolve() {
                return privateDataRoot;
            }
        };
    }

    interface PrivateDataRootProvider {
        Path resolve() throws IOException;
    }
}
