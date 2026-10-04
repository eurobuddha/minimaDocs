package org.mininotes.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** CI gate for MiniwebUrl routing (per-site origin + traversal defence) and MimeTypes. */
public class MiniwebUrlTest {

    static final String MX = "Mx200GZ47HZKRAZKC99PZQVKUD4ER2G78NN6BT";
    static final String HOST = MX.toLowerCase() + ".miniweb.local";

    @Test public void routesSiteAndAsset() {
        MiniwebUrl.Route s = MiniwebUrl.route(HOST, "/mxsites/" + MX + "/sub/p.html?q=1#x");
        assertEquals(MiniwebUrl.Kind.SITE, s.kind);
        assertEquals(MX, s.mx);
        assertEquals("sub/p.html", s.relPath);

        MiniwebUrl.Route a = MiniwebUrl.route(HOST, "/miniweb/mds.js");
        assertEquals(MiniwebUrl.Kind.ASSET, a.kind);
        assertEquals("mds.js", a.relPath);
    }

    @Test public void pageUrlRoundTrips() {
        String url = MiniwebUrl.pageUrl(MX, null);
        assertEquals("https://" + HOST + "/mxsites/" + MX + "/index.html", url);
    }

    @Test public void blocksTraversalAndCrossOrigin() {
        assertEquals(MiniwebUrl.Kind.BLOCKED, MiniwebUrl.route(HOST, "/mxsites/" + MX + "/../../etc").kind);
        assertEquals(MiniwebUrl.Kind.BLOCKED, MiniwebUrl.route(HOST, "/miniweb/%2e%2e/x").kind);
        assertEquals(MiniwebUrl.Kind.BLOCKED, MiniwebUrl.route(HOST, "/miniweb/..%2fx").kind);
        assertEquals(MiniwebUrl.Kind.BLOCKED, MiniwebUrl.route(HOST, "/mxsites/OTHER/x").kind);
        assertEquals(MiniwebUrl.Kind.BLOCKED, MiniwebUrl.route("evil.com", "/mxsites/" + MX + "/x").kind);
        assertEquals(MiniwebUrl.Kind.BLOCKED, MiniwebUrl.route(HOST, "/etc/passwd").kind);
        assertEquals(MiniwebUrl.Kind.BLOCKED, MiniwebUrl.route(HOST, "/mxsites/" + MX + "/a\\b").kind);
    }

    @Test public void mimeTypes() {
        assertEquals("text/html", MimeTypes.forPath("x/index.html"));
        assertEquals("image/svg+xml", MimeTypes.forPath("logo.svg"));
        assertEquals("font/woff2", MimeTypes.forPath("f.woff2"));
        assertEquals("application/octet-stream", MimeTypes.forPath("noext"));
        assertTrue(MimeTypes.isText("application/javascript"));
        assertFalse(MimeTypes.isText("image/png"));
    }
}
