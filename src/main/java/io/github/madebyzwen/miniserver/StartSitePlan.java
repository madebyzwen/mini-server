package io.github.madebyzwen.miniserver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class StartSitePlan {

    enum Kind { ROOT, APPLICATIONS, NONE }

    private final Kind kind;
    private final List<String> sites;
    private final String diagnostic;

    private StartSitePlan(Kind kind, List<String> sites, String diagnostic) {
        this.kind = kind;
        this.sites = Collections.unmodifiableList(new ArrayList<String>(sites));
        this.diagnostic = diagnostic;
    }

    static StartSitePlan root(String diagnostic) {
        return new StartSitePlan(Kind.ROOT, Collections.<String>emptyList(), diagnostic);
    }

    static StartSitePlan applications(List<String> sites) {
        return new StartSitePlan(Kind.APPLICATIONS, sites, null);
    }

    static StartSitePlan none(String diagnostic) {
        return new StartSitePlan(Kind.NONE, Collections.<String>emptyList(), diagnostic);
    }

    Kind getKind() { return kind; }
    List<String> getSites() { return sites; }
    String getDiagnostic() { return diagnostic; }
}
