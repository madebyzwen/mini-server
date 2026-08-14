package io.github.madebyzwen.miniserver;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalServerUrlTest {

    @Test
    void constructsLoopbackUrlFromActivePortAndConfiguredTarget() {
        LocalServerUrl url = new LocalServerUrl("/example/");

        assertEquals("http://127.0.0.1:51847/example/", url.forPort(51847));
    }

    @Test
    void rejectsInvalidPortsAndNonAbsoluteTargets() {
        assertThrows(IllegalArgumentException.class, () -> new LocalServerUrl("example/"));

        LocalServerUrl url = new LocalServerUrl("/example/");
        assertThrows(IllegalArgumentException.class, () -> url.forPort(0));
        assertThrows(IllegalArgumentException.class, () -> url.forPort(65536));
    }
}
