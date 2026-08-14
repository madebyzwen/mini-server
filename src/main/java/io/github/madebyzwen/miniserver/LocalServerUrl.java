package io.github.madebyzwen.miniserver;

/**
 * Constructs the local application URL from validated startup state.
 */
final class LocalServerUrl {

    private static final String ORIGIN_PREFIX = "http://127.0.0.1:";

    private final String startTarget;

    LocalServerUrl(String startTarget) {
        if (startTarget == null || !startTarget.startsWith("/")) {
            throw new IllegalArgumentException("The start target must be an absolute URL path.");
        }
        this.startTarget = startTarget;
    }

    String forPort(int activePort) {
        if (activePort < 1 || activePort > 65535) {
            throw new IllegalArgumentException("The active port is outside the TCP port range.");
        }
        return ORIGIN_PREFIX + activePort + startTarget;
    }
}
