package io.github.madebyzwen.miniserver;

import java.nio.file.Path;

final class ResolvedPersistenceTarget {

    private final String site;
    private final PersistenceScope scope;
    private final String operation;
    private final Path dataFile;
    private final Path legacyDataFile;

    ResolvedPersistenceTarget(
            String site,
            PersistenceScope scope,
            String operation,
            Path dataFile) {
        this(site, scope, operation, dataFile, null);
    }

    ResolvedPersistenceTarget(
            String site,
            PersistenceScope scope,
            String operation,
            Path dataFile,
            Path legacyDataFile) {
        this.site = site;
        this.scope = scope;
        this.operation = operation;
        this.dataFile = dataFile;
        this.legacyDataFile = legacyDataFile;
    }

    String getSite() {
        return site;
    }

    PersistenceScope getScope() {
        return scope;
    }

    String getOperation() {
        return operation;
    }

    Path getDataFile() {
        return dataFile;
    }

    Path getLegacyDataFile() {
        return legacyDataFile;
    }
}
