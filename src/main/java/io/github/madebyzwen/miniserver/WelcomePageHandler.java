package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Serves the Mini Server start-site selection page at the loopback root. */
final class WelcomePageHandler implements HttpHandler {

    private static final String DISPLAY_CONFIGURATION_PATH =
            "%APPDATA%\\MiniServer\\Config\\start-sites.txt";

    private final ConfiguredStartSiteProvider startSites;

    WelcomePageHandler(ConfiguredStartSiteProvider startSites) {
        this.startSites = startSites;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            respond(exchange, 405, "text/plain; charset=utf-8", "Method Not Allowed");
            return;
        }

        ConfiguredStartSiteProvider.SharedStartSites shared;
        try {
            shared = startSites.loadSharedStartSites();
        } catch (IOException | RuntimeException exception) {
            shared = ConfiguredStartSiteProvider.SharedStartSites.unavailable();
        }
        respond(exchange, 200, "text/html; charset=utf-8",
                page(shared, DISPLAY_CONFIGURATION_PATH));
    }

    private static String page(
            ConfiguredStartSiteProvider.SharedStartSites shared,
            String privateConfigurationPath) {
        StringBuilder choices = new StringBuilder();
        if (!shared.isAvailable()) {
            choices.append("<p class=\"state\">Shared start-site approval is unavailable. "
                    + "No application selection can be saved.</p>");
        } else if (shared.getSites().isEmpty()) {
            choices.append("<p class=\"state\">No applications are currently approved "
                    + "for automatic opening.</p>"
                    + "<button type=\"submit\">Save empty selection</button>");
        } else {
            choices.append("<fieldset><legend>Applications to open on later starts</legend>");
            for (String site : shared.getSites()) {
                String escaped = escapeHtml(site);
                choices.append("<label><input type=\"checkbox\" name=\"site\" value=\"")
                        .append(escaped)
                        .append("\" checked> <span>")
                        .append(escaped)
                        .append("</span></label>");
            }
            choices.append("</fieldset><button type=\"submit\">Save selection</button>");
        }

        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>Welcome to Mini Server</title><style>"
                + ":root{font-family:system-ui,-apple-system,Segoe UI,sans-serif;color:#172033;"
                + "background:#f4f7fb}*{box-sizing:border-box}body{margin:0;padding:2rem 1rem}"
                + "main{max-width:42rem;margin:auto;background:white;padding:clamp(1.4rem,5vw,3rem);"
                + "border-radius:1rem;box-shadow:0 1rem 3rem #17315b1a}h1{margin-top:0;font-size:"
                + "clamp(2rem,7vw,3.2rem)}p{line-height:1.6}fieldset{border:0;padding:0;margin:2rem 0}"
                + "legend{font-weight:700;margin-bottom:.75rem}label{display:flex;gap:.5rem;padding:.65rem;"
                + "margin:.4rem 0;border:1px solid #d8dfeb;border-radius:.55rem}input{width:1.15rem}"
                + "button{border:0;border-radius:.55rem;background:#1557d5;color:white;font-weight:700;"
                + "padding:.8rem 1.1rem;cursor:pointer}label:hover{border-color:#7d9bd0}"
                + "input:focus-visible,button:focus-visible{outline:3px solid #8ab4ff;outline-offset:2px}"
                + ".path{overflow-wrap:anywhere;background:#f4f7fb;"
                + "padding:.65rem;border-radius:.45rem;font-family:ui-monospace,monospace;font-size:.85rem}"
                + "#status{min-height:1.5rem;font-weight:600}.state{padding:1rem;background:#fff5d9;"
                + "border-radius:.55rem}</style></head><body><main><h1>Welcome to Mini Server</h1>"
                + "<p>Choose which Shared-approved applications should open automatically on "
                + "later start actions. Saving creates a new personal selection and replaces "
                + "your existing personal start selection. The checked choices do not display "
                + "your current selection.</p>"
                + "<form id=\"selection\">" + choices + "</form><p id=\"status\" role=\"status\"></p>"
                + "<p>Your selection is stored at:</p><p class=\"path\">"
                + escapeHtml(privateConfigurationPath) + "</p><script>"
                + "const f=document.getElementById('selection'),s=document.getElementById('status');"
                + "f.addEventListener('submit',async e=>{e.preventDefault();s.textContent='Saving…';"
                + "const sites=[...f.querySelectorAll('input[name=site]:checked')].map(x=>x.value);"
                + "try{const r=await fetch('/__miniserver/start-sites',{method:'POST',headers:{"
                + "'Content-Type':'application/json'},body:JSON.stringify({sites})});"
                + "s.textContent=r.ok?'Selection saved.':'Selection could not be saved.'}"
                + "catch(e){s.textContent='Selection could not be saved.'}});</script></main></body></html>";
    }

    static String escapeHtml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static void respond(HttpExchange exchange, int status, String type, String content)
            throws IOException {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Content-Length", Integer.toString(body.length));
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
