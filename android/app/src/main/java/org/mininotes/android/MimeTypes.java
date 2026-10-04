// Reused from PocketWeb, eurobuddha.
package org.mininotes.android;

import java.util.HashMap;
import java.util.Map;

/** Extension → MIME type for serving site + asset files (mirrors pocketWeb/js/mimetypes.js coverage). */
public final class MimeTypes {

    private MimeTypes() {}

    private static final Map<String, String> MAP = new HashMap<>();
    static {
        MAP.put("html", "text/html");
        MAP.put("htm", "text/html");
        MAP.put("css", "text/css");
        MAP.put("js", "application/javascript");
        MAP.put("mjs", "application/javascript");
        MAP.put("json", "application/json");
        MAP.put("svg", "image/svg+xml");
        MAP.put("png", "image/png");
        MAP.put("jpg", "image/jpeg");
        MAP.put("jpeg", "image/jpeg");
        MAP.put("gif", "image/gif");
        MAP.put("webp", "image/webp");
        MAP.put("ico", "image/x-icon");
        MAP.put("bmp", "image/bmp");
        MAP.put("ttf", "font/ttf");
        MAP.put("otf", "font/otf");
        MAP.put("woff", "font/woff");
        MAP.put("woff2", "font/woff2");
        MAP.put("eot", "application/vnd.ms-fontobject");
        MAP.put("txt", "text/plain");
        MAP.put("md", "text/markdown");
        MAP.put("xml", "application/xml");
        MAP.put("wasm", "application/wasm");
        MAP.put("mp4", "video/mp4");
        MAP.put("webm", "video/webm");
        MAP.put("mp3", "audio/mpeg");
        MAP.put("ogg", "audio/ogg");
        MAP.put("wav", "audio/wav");
        MAP.put("pdf", "application/pdf");
        MAP.put("zip", "application/zip");
    }

    /** MIME for a path, defaulting to application/octet-stream. */
    public static String forPath(String path) {
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
        String m = MAP.get(ext);
        return m != null ? m : "application/octet-stream";
    }

    /** Text types get a charset; binary types don't. */
    public static boolean isText(String mime) {
        return mime.startsWith("text/") || mime.equals("application/javascript")
                || mime.equals("application/json") || mime.equals("image/svg+xml")
                || mime.equals("application/xml");
    }
}
