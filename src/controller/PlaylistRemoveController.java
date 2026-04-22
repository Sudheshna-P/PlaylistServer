package controller;

import http.HttpResponse;
import model.PlaylistModel;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
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

    public void handle(SocketChannel client, SelectionKey key,
                       String filename, boolean keepAlive) {
        try {
            if (filename == null || filename.isEmpty()) {
                HttpResponse.send(client, key,
                        HttpResponse.error(400, "Bad Request",
                                "Filename required").getBytes(), keepAlive);
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
                    "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n" +
                    result;

            HttpResponse.send(client, key,
                    response.getBytes(StandardCharsets.UTF_8), keepAlive);

        } catch (IOException | SQLException e) {
            HttpResponse.send(client, key,
                    HttpResponse.error(500, "Internal Server Error",
                            e.getMessage()).getBytes(), keepAlive);
        }
    }
}