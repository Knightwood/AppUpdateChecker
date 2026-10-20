package com.github.knightwood.downloader.core.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * deepseek
 */
public class URLUtilCompat {

    private static final Pattern CONTENT_DISPOSITION_PATTERN =
            Pattern.compile("attachment;\\s*filename\\s*=\\s*(\"?)([^\"]*)\\1\\s*$",
                    Pattern.CASE_INSENSITIVE);

    /**
     * 等价于 android.webkit.URLUtil.guessFileName()
     */
    public static String guessFileName(String url, String contentDisposition, String mimeType) {
        String filename = null;
        String extension = null;

        // 1. 优先从 Content-Disposition 解析
        if (contentDisposition != null) {
            filename = parseContentDisposition(contentDisposition);
            if (filename != null) {
                int index = filename.lastIndexOf('/') + 1;
                if (index > 0) {
                    filename = filename.substring(index);
                }
            }
        }

        // 2. 从 URL 解析
        if (filename == null) {
            String decodedUrl = null;
            if (url != null) {
                try {
                    decodedUrl = URLDecoder.decode(url, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    decodedUrl = url;
                }
            }

            if (decodedUrl != null) {
                int queryIndex = decodedUrl.indexOf('?');
                if (queryIndex > 0) {
                    decodedUrl = decodedUrl.substring(0, queryIndex);
                }
                if (!decodedUrl.endsWith("/")) {
                    int index = decodedUrl.lastIndexOf('/') + 1;
                    if (index > 0) {
                        filename = decodedUrl.substring(index);
                    }
                }
            }
        }

        // 3. 从 MIME 推断扩展名
        if (filename == null) {
            filename = "downloadfile";
        }
        int dotIndex = filename.indexOf('.');
        if (dotIndex < 0) {
            if (mimeType != null) {
                extension = mimeTypeToExtension(mimeType);
            }
            if (extension == null) {
                extension = "";
            }
            filename = filename + extension;
        } else {
            if (mimeType != null) {
                String typeFromExt = getMimeTypeFromExtension(filename);
                if (typeFromExt != null && !typeFromExt.equalsIgnoreCase(mimeType)) {
                    extension = mimeTypeToExtension(mimeType);
                    if (extension != null) {
                        filename = filename.substring(0, dotIndex) + extension;
                    }
                }
            }
        }
        return filename;
    }

    static String parseContentDisposition(String contentDisposition) {
        try {
            Matcher m = CONTENT_DISPOSITION_PATTERN.matcher(contentDisposition);
            if (m.find()) {
                return m.group(2);
            }
        } catch (IllegalStateException e) {
            // 忽略
        }
        return null;
    }

    static String mimeTypeToExtension(String mimeType) {
        // 用 java.net.URLConnection 的映射，或自己维护一个表
        String ext = MimeTypeMapCompat.getExtensionFromMimeType(mimeType);
        return (ext != null && !ext.isEmpty()) ? "." + ext : null;
    }

    static String getMimeTypeFromExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) return null;
        return MimeTypeMapCompat.getMimeTypeFromExtension(filename.substring(dot + 1));
    }
}
