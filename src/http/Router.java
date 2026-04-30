package http;

import controller.FileController;
import controller.LibraryController;
import controller.PlaylistController;
import controller.UploadController;
import util.PathResolver;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Router {

    private final UploadController uploadController;
    private final LibraryController libraryController;
    private final PlaylistController playlistController;
    private final FileController fileController;

    public Router(UploadController uploadController,
                  LibraryController libraryController,
                  PlaylistController playlistController,
                  FileController fileController) {
        this.uploadController  = uploadController;
        this.libraryController = libraryController;
        this.playlistController = playlistController;
        this.fileController    = fileController;
    }

    public int dispatch(String method, String path,
                        ResponseWriter writer,
                        String headers, byte[] data,
                        int end) throws IOException {

        // ── POST routes ───────────────────────────────────────────────────

        // POST /upload
        if (method.equals("POST") && path.equals("/upload")) {
            int alreadyBuffered = data.length - end;
            return uploadController.beginUpload(
                    writer.getKey(), writer.getClient(),
                    data, headers, end, alreadyBuffered, writer.isKeepAlive());
        }

        // POST /playlists — create new playlist
        if (method.equals("POST") && path.equals("/playlists")) {
            String body = readBody(headers, data, end);
            playlistController.create(writer, body);
            return end + bodyLength(headers);
        }

        // POST /playlists/:id/add
        if (method.equals("POST") && path.matches("/playlists/\\d+/add")) {
            int id = extractMiddleId(path);
            String body = readBody(headers, data, end);
            playlistController.addItem(writer, id, body);
            return end + bodyLength(headers);
        }

        // ── DELETE routes ─────────────────────────────────────────────────

        // DELETE /playlists/:id/items/:name
        if (method.equals("DELETE") && path.matches("/playlists/\\d+/items/.+")) {
            String[] segments = path.split("/");
            int id = Integer.parseInt(segments[2]);
            String filename = segments[4];
            playlistController.removeItem(writer, id, filename);
            return end;
        }

        // DELETE /playlists/:id
        if (method.equals("DELETE") && path.matches("/playlists/\\d+")) {
            int id = extractLastId(path);
            playlistController.delete(writer, id);
            return end;
        }

        // DELETE /uploads/:name
        if (method.equals("DELETE") && path.startsWith("/uploads/")) {
            String filename = path.substring("/uploads/".length());
            libraryController.delete(writer, filename);
            return end;
        }

        // ── non GET ───────────────────────────────────────────────────────

        if (!method.equals("GET")) {
            writer.write(HttpResponse.methodNotAllowed().getBytes());
            if (!writer.isKeepAlive()) writer.close();
            return end;
        }

        // ── GET routes ────────────────────────────────────────────────────

        if (path.equals("/")) path = "/index.html";

        // GET /playlists
        if (path.equals("/playlists")) {
            playlistController.list(writer);
            return end;
        }

        // GET /playlists/:id
        if (path.matches("/playlists/\\d+")) {
            int id = extractLastId(path);
            playlistController.get(writer, id);
            return end;
        }

        // GET /library or /uploads
        if (path.equals("/library") || path.equals("/uploads")) {
            libraryController.list(writer);
            return end;
        }

        // static file serving
        Path filePath = PathResolver.resolve(path);
        if (filePath == null) {
            writer.write(HttpResponse.notFound().getBytes());
            if (!writer.isKeepAlive()) writer.close();
            return end;
        }
        if (!PathResolver.isSafe(filePath)) {
            writer.write(HttpResponse.forbidden().getBytes());
            writer.close();
            return -1;
        }
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            writer.write(HttpResponse.notFound().getBytes());
            if (!writer.isKeepAlive()) writer.close();
            return end;
        }

        String rangeHeader = HttpParser.extractHeader(headers, "Range");
        fileController.handle(writer, filePath, path, rangeHeader);
        return end;
    }

    public void continueUpload(SelectionKey key, SocketChannel client,
                               UploadController.UploadState state,
                               ByteBuffer buffer) throws IOException {
        uploadController.continueUpload(key, client, state, buffer);
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private String readBody(String headers, byte[] data, int end) {
        int length = bodyLength(headers);
        if (length <= 0) return "";
        return new String(data, end, Math.min(length, data.length - end),
                StandardCharsets.UTF_8);
    }

    private int bodyLength(String headers) {
        String cl = HttpParser.extractHeader(headers, "Content-Length");
        if (cl == null) return 0;
        try { return Integer.parseInt(cl.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private int extractLastId(String path) {
        String[] segments = path.split("/");
        return Integer.parseInt(segments[segments.length - 1]);
    }

    private int extractMiddleId(String path) {
        return Integer.parseInt(path.split("/")[2]);
    }
}