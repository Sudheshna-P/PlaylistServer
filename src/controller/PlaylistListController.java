package controller;

import http.HttpResponse;
import http.ResponseWriter;
import model.PlaylistModel;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class PlaylistListController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistModel playlistModel;

    public PlaylistListController(PlaylistModel playlistModel) {
        this.playlistModel = playlistModel;
    }

    public void handle(ResponseWriter writer) {
        try {
            List<String[]> playlists = playlistModel.getAllPlaylists();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < playlists.size(); i++) {
                String[] p = playlists.get(i);
                json.append("{\"id\":").append(p[0])
                        .append(",\"name\":\"").append(p[1]).append("\"")
                        .append(",\"count\":").append(p[2]).append("}");
                if (i < playlists.size() - 1) json.append(",");
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

        } catch (Exception e) {
            try {
                writer.write(HttpResponse.error(500, "Internal Server Error",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        }
    }
}