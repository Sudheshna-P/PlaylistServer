package controller;

import http.HttpResponse;
import http.ResponseWriter;
import model.MediaFile;
import model.PlaylistModel;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class PlaylistGetController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistModel playlistModel;

    public PlaylistGetController(PlaylistModel playlistModel) {
        this.playlistModel = playlistModel;
    }

    public void handle(ResponseWriter writer, int playlistId) {
        try {
            List<MediaFile> items = playlistModel.getPlaylistItems(playlistId);

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < items.size(); i++) {
                json.append(items.get(i).toJson());
                if (i < items.size() - 1) json.append(",");
            }
            json.append("]");

            String body = json.toString();
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n" +
                    body;

            writer.write(response.getBytes(StandardCharsets.UTF_8));
            if (!writer.isKeepAlive()) writer.close();

        } catch (IllegalArgumentException e) {
            try {
                writer.write(HttpResponse.error(404, "Not Found",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        } catch (Exception e) {
            try {
                writer.write(HttpResponse.error(500, "Internal Server Error",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        }
    }
}