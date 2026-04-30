package http;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class HttpResponse {

    private HttpResponse() {}

    public static void send(SocketChannel client, SelectionKey key, byte[] data, boolean keepAlive) {
        try {
            client.write(ByteBuffer.wrap(data));
            if (!keepAlive) cancelAndClose(key, client);
        } catch (IOException e) {
            try { cancelAndClose(key, client); } catch (Exception ignored) {}
        }
    }

    public static void sendBytes(SocketChannel client, byte[] data) {
        try { client.write(ByteBuffer.wrap(data)); }
        catch (IOException ignored) {}
    }

    public static void cancelAndClose(SelectionKey key, SocketChannel client) {
        try { key.cancel(); } catch (Exception ignored) {}
        try { client.close(); } catch (Exception ignored) {}
    }

    public static String error(int code, String status, String msg) {
        String body = "<h1>" + code + " " + status + "</h1><p>" + msg + "</p>";
        return "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.length() + "\r\n\r\n" + body;
    }

    public static String notFound() { return error(404, "Not Found", "Resource not found"); }
    public static String methodNotAllowed() { return error(405, "Method Not Allowed", "Method not allowed"); }
    public static String forbidden() { return error(403, "Forbidden", "Access denied"); }
}