package util;

public class JsonWriter {

    private JsonWriter() {}

    // safely escape a string value
    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    public static String object(String... keyValues) {
        if (keyValues.length % 2 != 0)
            throw new IllegalArgumentException("Must be key-value pairs");
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < keyValues.length; i += 2) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escape(keyValues[i])).append("\":");
            sb.append("\"").append(escape(keyValues[i + 1])).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    public static String array(java.util.List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(items.get(i));
        }
        sb.append("]");
        return sb.toString();
    }
}