package controller;

import http.HttpResponse;
import http.ResponseWriter;
import model.PlaylistModel;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlaylistCreateController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistModel playlistModel;

    public PlaylistCreateController(PlaylistModel playlistModel) {
        this.playlistModel = playlistModel;
    }

    // body format: name=MyPlaylist&files=video1.mp4&files=photo.jpg
    public void handle(ResponseWriter writer, String body) {
        System.out.println("CREATE BODY: " + body);
        try {
            // format: playlistName|file1.mp4,file2.jpg
            String[] parts = body.split("\\|", 2);
            if (parts.length < 2) {
                writer.write(HttpResponse.error(400, "Bad Request",
                        "Invalid format").getBytes());
                return;
            }

            String name = parts[0].trim();
            String[] fileArray = parts[1].split(",");
            List<String> files = new ArrayList<>();
            for (String f : fileArray) {
                String trimmed = f.trim();
                if (!trimmed.isEmpty()) files.add(trimmed);
            }

            if (name.isEmpty()) {
                writer.write(HttpResponse.error(400, "Bad Request",
                        "Playlist name required").getBytes());
                return;
            }

            if (files.isEmpty()) {
                writer.write(HttpResponse.error(400, "Bad Request",
                        "Playlist must have at least one file").getBytes());
                return;
            }

            int id = playlistModel.create(name, files);

            String result = "{\"status\":\"created\",\"id\":" + id +
                    ",\"name\":\"" + name + "\",\"count\":" + files.size() + "}";

            String response = "HTTP/1.1 201 Created\r\n" +
                    "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + result.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n" +
                    result;

            writer.write(response.getBytes(StandardCharsets.UTF_8));
            if (!writer.isKeepAlive()) writer.close();

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
}