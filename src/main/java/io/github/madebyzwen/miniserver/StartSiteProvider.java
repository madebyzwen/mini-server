package io.github.madebyzwen.miniserver;

import java.io.IOException;

/**
 * Supplies the explicit browser-opening plan for the current start action.
 */
interface StartSiteProvider {

    StartSitePlan planStartSites() throws IOException;
}
