package http;
import java.util.Locale;

public class HttpParser {

    private HttpParser() {}

    public static String extractHeader(String headers, String name) {
        for (String line : headers.split("\r\n"))
            if (line.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT) + ":"))
                return line.substring(line.indexOf(':') + 1).trim();
        return null;
    }

    public static int findHeaderEnd(byte[] data, int start) {
        for (int i = start; i <= data.length - 4; i++)
            if (data[i] == '\r' && data[i+1] == '\n' && data[i+2] == '\r' && data[i+3] == '\n')
                return i;
        return -1;
    }

    public static String extractBoundary(String contentType) {
        for (String p : contentType.split(";")) {
            p = p.trim();
            if (p.toLowerCase().startsWith("boundary="))
                return p.substring("boundary=".length()).trim();
        }
        return null;
    }

    public static String extractFilename(String disposition) {
        if (disposition == null) return null;
        for (String p : disposition.split(";")) {
            p = p.trim();
            if (p.toLowerCase().startsWith("filename=")) {
                String v = p.substring("filename=".length()).trim();
                if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length() - 1);
                return v;
            }
        }
        return null;
    }

    public static String extractPartHeader(String partHeaders, String name) {
        for (String line : partHeaders.split("\r\n"))
            if (line.toLowerCase().startsWith(name.toLowerCase() + ":"))
                return line.substring(line.indexOf(':') + 1).trim();
        return null;
    }

    public static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++)
                if (data[i+j] != pattern[j]) continue outer;
            return i;
        }
        return -1;
    }
}