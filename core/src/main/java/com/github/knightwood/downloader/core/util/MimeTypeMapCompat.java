package com.github.knightwood.downloader.core.util;

/**
 * deepseek
 */
public class MimeTypeMapCompat {
    private static final java.util.Map<String, String> MIME_TO_EXT = new java.util.HashMap<>();
    private static final java.util.Map<String, String> EXT_TO_MIME = new java.util.HashMap<>();

    static {
        // 常见的可自行扩充；也可以遍历 URLConnection.getFileNameMap()
        put("application/pdf", "pdf");
        put("application/zip", "zip");
        put("image/jpeg", "jpg");
        put("image/png", "png");
        put("image/gif", "gif");
        put("text/plain", "txt");
        put("text/html", "html");
        put("application/json", "json");
        put("application/octet-stream", "bin");
    }

    private static void put(String mime, String ext) {
        MIME_TO_EXT.put(mime, ext);
        EXT_TO_MIME.put(ext, mime);
    }

    public static String getExtensionFromMimeType(String mime) {
        return MIME_TO_EXT.get(mime);
    }

    public static String getMimeTypeFromExtension(String ext) {
        return EXT_TO_MIME.get(ext.toLowerCase(java.util.Locale.ROOT));
    }
}
