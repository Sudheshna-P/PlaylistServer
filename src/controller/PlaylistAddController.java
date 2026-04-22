package controller;

import http.HttpResponse;
import http.ResponseWriter;
import model.PlaylistModel;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PlaylistAddController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistModel playlistModel;

    public PlaylistAddController(PlaylistModel playlistModel) {
        this.playlistModel = playlistModel;
    }

    public void handle(ResponseWriter writer, String body) {
        try {
            String filename = body.trim();

            if (filename.isEmpty()) {
                writer.write(HttpResponse.error(400, "Bad Request",
                        "Filename required").getBytes());
                return;
            }

            boolean added = playlistModel.add(filename);
            String result = added
                    ? "{\"status\":\"added\",\"name\":\"" + filename + "\"}"
                    : "{\"status\":\"already exists\",\"name\":\"" + filename + "\"}";

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