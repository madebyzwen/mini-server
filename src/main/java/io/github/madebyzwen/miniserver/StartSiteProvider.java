package io.github.madebyzwen.miniserver;

import java.io.IOException;
import java.util.List;

/**
 * Supplies the effective application names for the current start action.
 */
interface StartSiteProvider {

    List<String> loadStartSites() throws IOException;
}
