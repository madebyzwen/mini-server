package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WindowsDefaultBrowserLauncherTest {

    @Test
    void delegatesTheSuppliedHttpUrlAsAUriWithoutBrowserExecutableDiscovery()
            throws Exception {
        RecordingDesktopAdapter desktopAdapter = new RecordingDesktopAdapter(true, true);
        WindowsDefaultBrowserLauncher launcher =
                new WindowsDefaultBrowserLauncher(desktopAdapter);

        launcher.open("http://127.0.0.1:51847/example/");

        assertEquals(
                URI.create("http://127.0.0.1:51847/example/"),
                desktopAdapter.browsedUri);
    }

    @Test
    void reportsFailureWithoutOpeningWhenDesktopBrowsingIsUnavailable() {
        RecordingDesktopAdapter desktopAdapter = new RecordingDesktopAdapter(false, true);
        WindowsDefaultBrowserLauncher launcher =
                new WindowsDefaultBrowserLauncher(desktopAdapter);

        assertThrows(
                IOException.class,
                () -> launcher.open("http://127.0.0.1:51847/example/"));

        assertFalse(desktopAdapter.browseCalled);
    }

    @Test
    void reportsFailureWithoutOpeningWhenBrowseActionIsUnsupported() {
        RecordingDesktopAdapter desktopAdapter = new RecordingDesktopAdapter(true, false);
        WindowsDefaultBrowserLauncher launcher =
                new WindowsDefaultBrowserLauncher(desktopAdapter);

        assertThrows(
                IOException.class,
                () -> launcher.open("http://127.0.0.1:51847/example/"));

        assertFalse(desktopAdapter.browseCalled);
    }

    @Test
    void propagatesAnUnderlyingDesktopBrowseFailure() {
        IOException browseFailure = new IOException("deliberate browse failure");
        RecordingDesktopAdapter desktopAdapter = new RecordingDesktopAdapter(true, true);
        desktopAdapter.browseFailure = browseFailure;
        WindowsDefaultBrowserLauncher launcher =
                new WindowsDefaultBrowserLauncher(desktopAdapter);

        IOException actual = assertThrows(
                IOException.class,
                () -> launcher.open("http://127.0.0.1:51847/example/"));

        assertSame(browseFailure, actual);
    }

    @Test
    void rejectsMalformedOrNonHttpUrlsBeforeDesktopDelegation() {
        RecordingDesktopAdapter desktopAdapter = new RecordingDesktopAdapter(true, true);
        WindowsDefaultBrowserLauncher launcher =
                new WindowsDefaultBrowserLauncher(desktopAdapter);

        assertThrows(IOException.class, () -> launcher.open("not a valid URI"));
        assertThrows(IOException.class, () -> launcher.open("file:///tmp/example"));
        assertFalse(desktopAdapter.browseCalled);
    }

    private static final class RecordingDesktopAdapter
            implements WindowsDefaultBrowserLauncher.DesktopAdapter {

        private final boolean desktopSupported;
        private final boolean browseSupported;
        private boolean browseCalled;
        private URI browsedUri;
        private IOException browseFailure;

        private RecordingDesktopAdapter(boolean desktopSupported, boolean browseSupported) {
            this.desktopSupported = desktopSupported;
            this.browseSupported = browseSupported;
        }

        @Override
        public boolean isDesktopSupported() {
            return desktopSupported;
        }

        @Override
        public boolean isBrowseSupported() {
            return browseSupported;
        }

        @Override
        public void browse(URI uri) throws IOException {
            browseCalled = true;
            browsedUri = uri;
            if (browseFailure != null) {
                throw browseFailure;
            }
        }
    }
}
