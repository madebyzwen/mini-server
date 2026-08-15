package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalServerUrlTest {

    @Test
    void constructsLoopbackUrlFromActivePortAndConfiguredTarget() {
        LocalServerUrl url = new LocalServerUrl("example");

        assertEquals("http://127.0.0.1:51847/example/", url.forPort(51847));
    }

    @Test
    void encodesOneApplicationNameAsOneUrlPathSegment() {
        assertEquals(
                "http://127.0.0.1:51847/my%20caf%C3%A9%25/",
                new LocalServerUrl("my café%").forPort(51847));
    }

    @Test
    void rejectsInvalidPortsAndNonAbsoluteTargets() {
        assertThrows(IllegalArgumentException.class, () -> new LocalServerUrl("first/second"));
        assertThrows(IllegalArgumentException.class, () -> new LocalServerUrl("http:example"));

        LocalServerUrl url = new LocalServerUrl("example");
        assertThrows(IllegalArgumentException.class, () -> url.forPort(0));
        assertThrows(IllegalArgumentException.class, () -> url.forPort(65536));
    }
}
