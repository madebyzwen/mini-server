package io.github.madebyzwen.miniserver;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Delegates HTTP URLs to the browser configured by Windows.
 */
final class WindowsDefaultBrowserLauncher implements BrowserLauncher {

    private final DesktopAdapter desktopAdapter;

    WindowsDefaultBrowserLauncher() {
        this(new AwtDesktopAdapter());
    }

    WindowsDefaultBrowserLauncher(DesktopAdapter desktopAdapter) {
        if (desktopAdapter == null) {
            throw new NullPointerException("The desktop adapter must not be null.");
        }
        this.desktopAdapter = desktopAdapter;
    }

    @Override
    public void open(String url) throws IOException {
        URI uri = toHttpUri(url);
        try {
            if (!desktopAdapter.isDesktopSupported()
                    || !desktopAdapter.isBrowseSupported()) {
                throw new IOException("Desktop browser integration is not available.");
            }
            desktopAdapter.browse(uri);
        } catch (IOException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IOException("The default browser could not be opened.", exception);
        }
    }

    private static URI toHttpUri(String url) throws IOException {
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException | NullPointerException exception) {
            throw new IOException("The browser URL is invalid.", exception);
        }

        if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("The browser URL must be an absolute HTTP URI.");
        }
        return uri;
    }

    interface DesktopAdapter {

        boolean isDesktopSupported();

        boolean isBrowseSupported();

        void browse(URI uri) throws IOException;
    }

    private static final class AwtDesktopAdapter implements DesktopAdapter {

        @Override
        public boolean isDesktopSupported() {
            return Desktop.isDesktopSupported();
        }

        @Override
        public boolean isBrowseSupported() {
            return Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
        }

        @Override
        public void browse(URI uri) throws IOException {
            Desktop.getDesktop().browse(uri);
        }
    }
}
