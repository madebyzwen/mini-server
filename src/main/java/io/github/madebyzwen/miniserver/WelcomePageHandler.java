package io.github.madebyzwen.miniserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/** Serves the Mini Server start-site setup, editing, and recovery page. */
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

        respond(exchange, 200, "text/html; charset=utf-8",
                page(startSites.loadRootPageState(), DISPLAY_CONFIGURATION_PATH));
    }

    private static String page(
            ConfiguredStartSiteProvider.RootPageState state,
            String privateConfigurationPath) {
        ConfiguredStartSiteProvider.SharedStartSites shared = state.getShared();
        Set<String> selected = new HashSet<String>(state.getSelectedSites());
        StringBuilder content = new StringBuilder();

        if (!shared.isAvailable()) {
            content.append("<p class=\"state warning\">Shared start-site approval cannot "
                    + "currently be read. No application selection can be saved.</p>");
        } else if (shared.getSites().isEmpty()) {
            content.append("<p class=\"state\">There are currently no applications "
                    + "available to select or save.</p>");
        } else {
            if (state.getPrivateState()
                    == ConfiguredStartSiteProvider.PrivateSelectionState.MISSING) {
                content.append("<p class=\"state\">Choose at least one application. "
                        + "Nothing is saved until Save and open succeeds.</p>");
            } else if (state.getPrivateState()
                    == ConfiguredStartSiteProvider.PrivateSelectionState.UNREADABLE) {
                content.append("<p class=\"state warning\">Your existing personal "
                        + "selection could not be read. No saved selection has been "
                        + "guessed; choose a replacement selection.</p>");
            } else {
                content.append("<p class=\"state\">This page shows your current personal "
                        + "selection among the applications currently approved in Shared.</p>");
            }

            content.append("<fieldset><legend>Applications</legend>");
            for (String site : shared.getSites()) {
                String escaped = escapeHtml(site);
                content.append("<label><input type=\"checkbox\" name=\"site\" value=\"")
                        .append(escaped)
                        .append("\"");
                if (selected.contains(site)) {
                    content.append(" checked");
                }
                content.append("> <span>")
                        .append(escaped)
                        .append("</span></label>");
            }
            content.append("</fieldset><button id=\"save\" type=\"submit\"");
            if (selected.isEmpty()) {
                content.append(" disabled");
            }
            content.append(">Save and open</button>");
        }

        String script = state.isSavingAvailable()
                ? "<script>const f=document.getElementById('selection'),"
                + "s=document.getElementById('status'),b=document.getElementById('save'),"
                + "inputs=[...f.querySelectorAll('input[name=site]')];let saving=false;"
                + "const chosen=()=>inputs.filter(x=>x.checked).map(x=>x.value);"
                + "const update=()=>{b.disabled=saving||chosen().length===0};"
                + "inputs.forEach(x=>x.addEventListener('change',update));update();"
                + "f.addEventListener('submit',async e=>{e.preventDefault();"
                + "if(saving)return;const sites=chosen();if(sites.length===0){update();return;}"
                + "saving=true;update();s.textContent='Saving selection…';try{"
                + "const r=await fetch('/__miniserver/start-sites',{method:'POST',headers:{"
                + "'Content-Type':'application/json'},body:JSON.stringify({sites})});"
                + "if(!r.ok)throw new Error('save failed');const result=await r.json();"
                + "if(!result||!Array.isArray(result.targets)||result.targets.length===0"
                + "||typeof result.targets[0]!=='string')throw new Error('invalid response');"
                + "s.textContent='Selection saved. Opening applications…';"
                + "window.location.replace(result.targets[0]);}catch(error){saving=false;"
                + "s.textContent='Selection could not be saved. Please try again.';update();}});"
                + "</script>"
                : "";

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
                + "padding:.8rem 1.1rem;cursor:pointer}button:disabled{opacity:.5;cursor:not-allowed}"
                + "label:hover{border-color:#7d9bd0}input:focus-visible,button:focus-visible{"
                + "outline:3px solid #8ab4ff;outline-offset:2px}.path{overflow-wrap:anywhere;"
                + "background:#f4f7fb;padding:.65rem;border-radius:.45rem;"
                + "font-family:ui-monospace,monospace;font-size:.85rem}#status{min-height:1.5rem;"
                + "font-weight:600}.state{padding:1rem;background:#edf6ff;border-radius:.55rem}"
                + ".warning{background:#fff5d9}</style></head><body><main>"
                + "<h1>Welcome to Mini Server</h1>"
                + "<p>Save replaces your complete personal selection, opens the normalized "
                + "selection immediately, and uses it for future normal starts.</p>"
                + "<form id=\"selection\">" + content + "</form>"
                + "<p id=\"status\" role=\"status\"></p>"
                + "<p>Your selection is stored at:</p><p class=\"path\">"
                + escapeHtml(privateConfigurationPath) + "</p>" + script
                + "</main></body></html>";
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
