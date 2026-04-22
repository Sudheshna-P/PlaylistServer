package controller;

import http.HttpResponse;
import http.ResponseWriter;
import model.PlaylistModel;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PlaylistRemoveController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistModel playlistModel;

    public PlaylistRemoveController(PlaylistModel playlistModel) {
        this.playlistModel = playlistModel;
    }

    public void handle(ResponseWriter writer, String filename) {
        try {
            if (filename == null || filename.isEmpty()) {
                writer.write(HttpResponse.error(400, "Bad Request",
                        "Filename required").getBytes());
                return;
            }

            boolean removed = playlistModel.remove(filename);
            String result = removed
                    ? "{\"status\":\"removed\",\"name\":\"" + filename + "\"}"
                    : "{\"status\":\"not found\",\"name\":\"" + filename + "\"}";

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