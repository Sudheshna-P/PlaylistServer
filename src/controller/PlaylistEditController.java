package controller;

import http.HttpResponse;
import http.ResponseWriter;
import model.PlaylistModel;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PlaylistEditController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistModel playlistModel;

    public PlaylistEditController(PlaylistModel playlistModel) {
        this.playlistModel = playlistModel;
    }

    // POST /playlists/:id/add  body = filename
    public void handleAdd(ResponseWriter writer, int playlistId, String body) {
        try {
            String filename = body.trim();
            if (filename.isEmpty()) {
                writer.write(HttpResponse.error(400, "Bad Request",
                        "Filename required").getBytes());
                return;
            }

            playlistModel.addItem(playlistId, filename);

            String result = "{\"status\":\"added\",\"name\":\"" + filename + "\"}";
            sendJson(writer, result);

        } catch (IllegalArgumentException e) {
            try {
                writer.write(HttpResponse.error(400, "Bad Request",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                writer.write(HttpResponse.error(500, "Internal Server Error",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        }
    }

    // DELETE /playlists/:id/items/:name
    public void handleRemove(ResponseWriter writer, int playlistId, String filename) {
        try {
            playlistModel.removeItem(playlistId, filename);

            String result = "{\"status\":\"removed\",\"name\":\"" + filename + "\"}";
            sendJson(writer, result);

        } catch (IllegalArgumentException e) {
            try {
                writer.write(HttpResponse.error(400, "Bad Request",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                writer.write(HttpResponse.error(500, "Internal Server Error",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        }
    }

    private void sendJson(ResponseWriter writer, String result) throws Exception {
        String response = "HTTP/1.1 200 OK\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + result.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n" +
                result;
        writer.write(response.getBytes(StandardCharsets.UTF_8));
        if (!writer.isKeepAlive()) writer.close();
    }
}