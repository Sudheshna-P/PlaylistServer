package controller;

import http.HttpResponse;
import http.ResponseWriter;
import logger.LoggerManager;
import model.MediaFile;
import service.PlaylistService;
import service.ServiceException;
import util.JsonWriter;

import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PlaylistController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final PlaylistService playlistService;

    public PlaylistController(PlaylistService playlistService) {
        this.playlistService = playlistService;
    }

    // GET /playlists
    public void list(ResponseWriter writer) {
        try {
            List<String[]> playlists = playlistService.getAll();
            List<String> items = new ArrayList<>();
            for (String[] p : playlists) {
                items.add(JsonWriter.object("id", p[0], "name", p[1], "count", p[2]));
            }
            sendJson(writer, 200, "OK", JsonWriter.array(items));
        } catch (ServiceException e) {
            sendError(writer, e);
        } catch (Exception e) {
            log("list", e);
            sendServerError(writer);
        }
    }

    // GET /playlists/:id
    public void get(ResponseWriter writer, int id) {
        try {
            List<MediaFile> files = playlistService.getItems(id);
            List<String> items = new ArrayList<>();
            for (MediaFile f : files) {
                items.add(JsonWriter.object("name", f.getName(), "type", f.getType()));
            }
            sendJson(writer, 200, "OK", JsonWriter.array(items));
        } catch (ServiceException e) {
            sendError(writer, e);
        } catch (Exception e) {
            log("get", e);
            sendServerError(writer);
        }
    }

    // POST /playlists
    public void create(ResponseWriter writer, String body) {
        try {
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

            int id = playlistService.create(name, files);
            sendJson(writer, 201, "Created", JsonWriter.object(
                    "status", "created",
                    "id",     String.valueOf(id),
                    "name",   name,
                    "count",  String.valueOf(files.size())));
        } catch (ServiceException e) {
            sendError(writer, e);
        } catch (Exception e) {
            log("create", e);
            sendServerError(writer);
        }
    }

    // POST /playlists/:id/add
    public void addItem(ResponseWriter writer, int id, String body) {
        try {
            String filename = body.trim();
            playlistService.addItem(id, filename);
            sendJson(writer, 200, "OK",
                    JsonWriter.object("status", "added", "name", filename));
        } catch (ServiceException e) {
            sendError(writer, e);
        } catch (Exception e) {
            log("addItem", e);
            sendServerError(writer);
        }
    }

    // DELETE /playlists/:id/items/:name
    public void removeItem(ResponseWriter writer, int id, String filename) {
        try {
            playlistService.removeItem(id, filename);
            sendJson(writer, 200, "OK",
                    JsonWriter.object("status", "removed", "name", filename));
        } catch (ServiceException e) {
            sendError(writer, e);
        } catch (Exception e) {
            log("removeItem", e);
            sendServerError(writer);
        }
    }

    // DELETE /playlists/:id
    public void delete(ResponseWriter writer, int id) {
        try {
            playlistService.delete(id);
            sendJson(writer, 200, "OK",
                    JsonWriter.object("status", "deleted", "id", String.valueOf(id)));
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
                "[ERROR] PlaylistController." + method + ": " + e.getMessage());
    }
}