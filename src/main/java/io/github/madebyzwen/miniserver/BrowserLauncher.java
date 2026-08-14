package io.github.madebyzwen.miniserver;

import java.io.IOException;

/**
 * Requests that a browser open a URL without owning the browser process lifetime.
 */
interface BrowserLauncher {

    void open(String url) throws IOException;
}
