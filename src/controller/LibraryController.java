package controller;

import http.HttpResponse;
import http.ResponseWriter;
import logger.LoggerManager;
import model.MediaFile;
import service.LibraryService;
import service.ServiceException;
import util.JsonWriter;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LibraryController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final LibraryService libraryService;

    public LibraryController(LibraryService libraryService) {
        this.libraryService = libraryService;
    }

    // GET /library
    public void list(ResponseWriter writer) {
        try {
            List<MediaFile> files = libraryService.getAll();
            List<String> items = new ArrayList<>();
            for (MediaFile f : files) {
                items.add(JsonWriter.object("name", f.getName(), "type", f.getType()));
            }
            sendJson(writer, 200, "OK", JsonWriter.array(items));
        } catch (ServiceException e) {
            sendError(writer, e);
        } catch (Exception e) {
            log("list", e);
            sendServerError(writer);
        }
    }

    // DELETE /uploads/:name
    public void delete(ResponseWriter writer, String filename) {
        try {
            libraryService.delete(filename);
            sendJson(writer, 200, "OK",
                    JsonWriter.object("status", "deleted", "name", filename));
        } catch (ServiceException e) {
            sendError(writer, e);
        } catch (Exception e) {
            log("delete", e);
            sendServerError(writer);
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private void sendJson(ResponseWriter writer, int status,
                          String statusText, String body) {
        try {
            String response = "HTTP/1.1 " + status + " " + statusText + "\r\n" +
                    "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n" +
                    body;
            writer.write(response.getBytes(StandardCharsets.UTF_8));
            if (!writer.isKeepAlive()) writer.close();
        } catch (Exception ignored) {}
    }

    private void sendError(ResponseWriter writer, ServiceException e) {
        try {
            writer.write(HttpResponse.error(e.getStatusCode(),
                    "Error", e.getMessage()).getBytes());
        } catch (Exception ignored) {}
    }

    private void sendServerError(ResponseWriter writer) {
        try {
            writer.write(HttpResponse.error(500, "Internal Server Error",
                    "Something went wrong").getBytes());
        } catch (Exception ignored) {}
    }

    private void log(String method, Exception e) {
        LoggerManager.getInstance().info(
                "[ERROR] LibraryController." + method + ": " + e.getMessage());
    }
}