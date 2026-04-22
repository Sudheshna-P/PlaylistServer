package controller;

import http.HttpResponse;
import http.ResponseWriter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class DeleteMediaController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final String uploadsPath;

    public DeleteMediaController(String uploadsPath) {
        this.uploadsPath = uploadsPath;
    }

    public void handle(ResponseWriter writer, String filename) {
        if (filename == null || filename.isEmpty()
                || filename.contains("..") || filename.contains("/")) {
            try {
                writer.write(HttpResponse.forbidden().getBytes());
            } catch (Exception ignored) {}
            return;
        }

        try {
            java.nio.file.Path target = Paths.get(uploadsPath, filename);

            if (!Files.exists(target)) {
                writer.write(HttpResponse.notFound().getBytes());
                return;
            }

            Files.delete(target);

            String result = "{\"status\":\"deleted\",\"name\":\"" + filename + "\"}";
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + result.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n" +
                    result;

            writer.write(response.getBytes(StandardCharsets.UTF_8));
            if (!writer.isKeepAlive()) writer.close();

        } catch (Exception e) {
            try {
                writer.write(HttpResponse.error(500, "Internal Server Error",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        }
    }
}