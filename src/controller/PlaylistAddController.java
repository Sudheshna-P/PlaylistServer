package controller;

import http.HttpParser;
import http.HttpResponse;
import model.PlaylistModel;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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

    public void handle(SocketChannel client, SelectionKey key,
                       String body, boolean keepAlive) {
        try {
            // body is plain filename sent as text
            String filename = body.trim();

            if (filename.isEmpty()) {
                HttpResponse.send(client, key,
                        HttpResponse.error(400, "Bad Request",
                                "Filename required").getBytes(), keepAlive);
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