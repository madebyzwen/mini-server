package io.github.madebyzwen.miniserver;

enum PersistenceScope {
    SHARED("shared"),
    PRIVATE("private");

    private final String pathComponent;

    PersistenceScope(String pathComponent) {
        this.pathComponent = pathComponent;
    }

    static PersistenceScope fromPathComponent(String pathComponent) {
        for (PersistenceScope scope : values()) {
            if (scope.pathComponent.equals(pathComponent)) {
                return scope;
            }
        }
        return null;
    }
}
