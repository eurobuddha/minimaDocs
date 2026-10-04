// Reused from PocketWeb, eurobuddha.
package org.mininotes.android;

/**
 * URL scheme + routing for the native site WebView, and the pure path-safety logic behind
 * SiteServer.shouldInterceptRequest (kept separate so it is unit-testable off-device).
 *
 * Each site renders under its OWN origin so sites are mutually isolated and isolated from the app:
 *   site page : https://&lt;mx-lower&gt;.miniweb.local/mxsites/&lt;mx&gt;/&lt;path&gt;
 *   shared lib: https://&lt;mx-lower&gt;.miniweb.local/miniweb/&lt;path&gt;
 * Serving the page two path-levels deep (…/mxsites/&lt;mx&gt;/) makes every site's hard-coded
 * "../../miniweb/…" reference resolve to "/miniweb/…", exactly as the MDS "/root/mxsites/&lt;Mx&gt;/"
 * → "/root/miniweb/" layout did. Anything outside these two subtrees is refused.
 */
public final class MiniwebUrl {

    private MiniwebUrl() {}

    public static final String HOST_SUFFIX = ".miniweb.local";
    public static final String SCHEME = "https";

    public enum Kind { SITE, ASSET, BLOCKED }

    public static final class Route {
        public final Kind kind;
        public final String mx;        // the site's Mx name (SITE only), else null
        public final String relPath;   // path within the site or the miniweb asset tree
        Route(Kind kind, String mx, String relPath) { this.kind = kind; this.mx = mx; this.relPath = relPath; }
        static Route blocked() { return new Route(Kind.BLOCKED, null, null); }
    }

    /** The URL to load a site's page in the WebView. */
    public static String pageUrl(String mx, String path) {
        String p = path == null || path.isEmpty() ? "index.html" : stripLeadingSlash(path);
        return SCHEME + "://" + mx.toLowerCase(java.util.Locale.ROOT) + HOST_SUFFIX + "/mxsites/" + mx + "/" + p;
    }

    public static boolean isMiniwebHost(String host) {
        return host != null && host.endsWith(HOST_SUFFIX);
    }

    /**
     * Classify an intercepted request URL. Returns BLOCKED unless it is a SITE file under
     * /mxsites/&lt;mx&gt;/ whose &lt;mx&gt; matches the host, or a shared /miniweb/ asset — and only after the
     * relative path is confirmed to stay inside its subtree (no traversal).
     */
    public static Route route(String host, String rawPath) {
        if (!isMiniwebHost(host)) return Route.blocked();
        String hostMx = host.substring(0, host.length() - HOST_SUFFIX.length());
        String path = decodeAndNormalize(rawPath);
        if (path == null) return Route.blocked();       // traversal / illegal

        if (path.startsWith("/mxsites/")) {
            String rest = path.substring("/mxsites/".length());
            int slash = rest.indexOf('/');
            if (slash <= 0) return Route.blocked();
            String mx = rest.substring(0, slash);
            String rel = rest.substring(slash + 1);
            if (!mx.toLowerCase(java.util.Locale.ROOT).equals(hostMx)) return Route.blocked();   // origin must match its site
            if (rel.isEmpty()) rel = "index.html";
            return new Route(Kind.SITE, mx, rel);
        }
        if (path.startsWith("/miniweb/")) {
            String rel = path.substring("/miniweb/".length());
            if (rel.isEmpty()) return Route.blocked();
            return new Route(Kind.ASSET, null, rel);
        }
        return Route.blocked();
    }

    /**
     * Percent-decode, drop any query/fragment, collapse ".", and REJECT (return null) any path that
     * uses ".." or backslashes or is not absolute. The result is a clean absolute path with no segment
     * that could escape its subtree.
     */
    static String decodeAndNormalize(String rawPath) {
        if (rawPath == null || rawPath.isEmpty()) return null;
        String p = rawPath;
        int q = p.indexOf('?'); if (q >= 0) p = p.substring(0, q);
        int h = p.indexOf('#'); if (h >= 0) p = p.substring(0, h);
        p = percentDecode(p);
        if (p.indexOf('\\') >= 0 || p.indexOf('\0') >= 0) return null;
        if (!p.startsWith("/")) return null;
        StringBuilder out = new StringBuilder();
        for (String seg : p.split("/", -1)) {
            if (seg.isEmpty() || seg.equals(".")) continue;
            if (seg.equals("..")) return null;      // no traversal, ever
            out.append('/').append(seg);
        }
        return out.length() == 0 ? "/" : out.toString();
    }

    private static String percentDecode(String s) {
        if (s.indexOf('%') < 0) return s;
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < s.length(); ) {
            char c = s.charAt(i);
            if (c == '%' && i + 2 < s.length()) {
                int hi = Character.digit(s.charAt(i + 1), 16), lo = Character.digit(s.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) { bos.write('%'); i++; }
                else { bos.write((hi << 4) | lo); i += 3; }
            } else {
                for (byte b : String.valueOf(c).getBytes(java.nio.charset.StandardCharsets.UTF_8)) bos.write(b);
                i++;
            }
        }
        return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String stripLeadingSlash(String s) { return s.startsWith("/") ? s.substring(1) : s; }
}
