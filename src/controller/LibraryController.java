package controller;

import http.HttpResponse;
import model.LibraryModel;
import model.MediaFile;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class LibraryController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final LibraryModel libraryModel;

    public LibraryController(LibraryModel libraryModel) {
        this.libraryModel = libraryModel;
    }

    public void handle(SocketChannel client, SelectionKey key, boolean keepAlive) {
        try {
            List<MediaFile> files = libraryModel.getAll();

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
                    "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n" +
                    body;

            HttpResponse.send(client, key, response.getBytes(), keepAlive);

        } catch (IOException e) {
            HttpResponse.send(client, key,
                    HttpResponse.notFound().getBytes(), keepAlive);
        }
    }
}