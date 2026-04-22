package controller;

import http.HttpResponse;
import http.ResponseWriter;
import model.MediaFile;
import model.PlaylistModel;

import java.util.List;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PlaylistController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistModel playlistModel;

    public PlaylistController(PlaylistModel playlistModel) {
        this.playlistModel = playlistModel;
    }

    public void handle(ResponseWriter writer) {
        try {
            List<MediaFile> files = playlistModel.getAll();

            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < files.size(); i++) {
                json.append(files.get(i).toJson());
                if (i < files.size() - 1) json.append(",");
            }
            json.append("]");

            String body = json.toString();
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + body.getBytes().length + "\r\n" +
                    "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n" +
                    body;

            writer.write(response.getBytes());
            if (!writer.isKeepAlive()) writer.close();

        } catch (Exception e) {
            try {
                writer.write(HttpResponse.error(500, "Internal Server Error",
                        e.getMessage()).getBytes());
            } catch (Exception ignored) {}
        }
    }
}