package io.github.madebyzwen.miniserver;

import java.nio.file.Path;

final class ResolvedPersistenceTarget {

    private final String site;
    private final PersistenceScope scope;
    private final String operation;
    private final Path dataFile;

    ResolvedPersistenceTarget(
            String site,
            PersistenceScope scope,
            String operation,
            Path dataFile) {
        this.site = site;
        this.scope = scope;
        this.operation = operation;
        this.dataFile = dataFile;
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
}
