package controller;

import http.HttpResponse;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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

    public void handle(SocketChannel client, SelectionKey key,
                       String filename, boolean keepAlive) {

        // basic safety check
        if (filename == null || filename.isEmpty()
                || filename.contains("..") || filename.contains("/")) {
            HttpResponse.send(client, key,
                    HttpResponse.forbidden().getBytes(), keepAlive);
            return;
        }

        try {
            java.nio.file.Path target = Paths.get(uploadsPath, filename);

            if (!Files.exists(target)) {
                HttpResponse.send(client, key,
                        HttpResponse.notFound().getBytes(), keepAlive);
                return;
            }

            Files.delete(target);

            String result = "{\"status\":\"deleted\",\"name\":\"" + filename + "\"}";
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + result.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n" +
                    result;

            HttpResponse.send(client, key,
                    response.getBytes(StandardCharsets.UTF_8), keepAlive);

        } catch (IOException e) {
            HttpResponse.send(client, key,
                    HttpResponse.error(500, "Internal Server Error",
                            e.getMessage()).getBytes(), keepAlive);
        }
    }
}