package util;

import java.util.Locale;

public class ContentType {

    private ContentType() {}

    public static String get(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=utf-8";
        if (p.endsWith(".css"))  return "text/css";
        if (p.endsWith(".js"))   return "application/javascript";
        if (p.endsWith(".png"))  return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif"))  return "image/gif";
        if (p.endsWith(".mp4"))  return "video/mp4";
        if (p.endsWith(".webm")) return "video/webm";
        if (p.endsWith(".ogg"))  return "video/ogg";
        if (p.endsWith(".pdf"))  return "application/pdf";
        if (p.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }
}