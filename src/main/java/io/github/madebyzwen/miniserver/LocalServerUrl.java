package io.github.madebyzwen.miniserver;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Constructs the local application URL from validated startup state.
 */
final class LocalServerUrl {

    private static final String ORIGIN_PREFIX = "http://127.0.0.1:";

    private final String encodedStartTarget;

    LocalServerUrl(String siteName) {
        if (!ConfiguredStartSiteProvider.isSafeApplicationName(siteName)) {
            throw new IllegalArgumentException("The start site must be a safe application name.");
        }
        try {
            this.encodedStartTarget = new URI(null, null, "/" + siteName + "/", null)
                    .toASCIIString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("The start site cannot be encoded.", exception);
        }
    }

    String forPort(int activePort) {
        if (activePort < 1 || activePort > 65535) {
            throw new IllegalArgumentException("The active port is outside the TCP port range.");
        }
        return ORIGIN_PREFIX + activePort + encodedStartTarget;
    }

    static String rootForPort(int activePort) {
        if (activePort < 1 || activePort > 65535) {
            throw new IllegalArgumentException("The active port is outside the TCP port range.");
        }
        return ORIGIN_PREFIX + activePort + "/";
    }
}
