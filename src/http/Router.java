package http;

import controller.FileController;
import controller.LibraryController;
import controller.PlaylistController;
import util.PathResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Router {

    private final LibraryController libraryController;
    private final PlaylistController playlistController;
    private final FileController fileController;

    public Router(LibraryController libraryController,
                  PlaylistController playlistController,
                  FileController fileController) {
        this.libraryController = libraryController;
        this.playlistController = playlistController;
        this.fileController = fileController;
    }

    public int dispatch(String method, String path, ResponseWriter writer,
                        String headers, byte[] data, int end) throws IOException {

        String[] segments = path.split("/");

        if (method.equals("POST")) {

            // POST /playlists
            if (path.equals("/playlists")) {
                String body = readBody(headers, data, end);
                playlistController.create(writer, body);
                return end + bodyLength(headers);
            }

            // POST /playlists/:id/add
            if (segments.length == 4
                    && segments[1].equals("playlists")
                    && isNumeric(segments[2])
                    && segments[3].equals("add")) {
                String body = readBody(headers, data, end);
                playlistController.addItem(writer, segments[2], body);
                return end + bodyLength(headers);
            }

            writer.write(HttpResponse.methodNotAllowed().getBytes());
            if (!writer.isKeepAlive()) writer.close();
            return end;
        }

        if (method.equals("DELETE")) {

            // DELETE /playlists/:id/items/:name
            if (segments.length == 5
                    && segments[1].equals("playlists")
                    && isNumeric(segments[2])
                    && segments[3].equals("items")) {
                playlistController.removeItem(writer, segments[2], segments[4]);
                return end;
            }

            // DELETE /playlists/:id
            if (segments.length == 3
                    && segments[1].equals("playlists")
                    && isNumeric(segments[2])) {
                playlistController.delete(writer, segments[2]);
                return end;
            }

            // DELETE /uploads/:name
            if (segments.length == 3
                    && segments[1].equals("uploads")) {
                libraryController.delete(writer, segments[2]);
                return end;
            }

            writer.write(HttpResponse.methodNotAllowed().getBytes());
            if (!writer.isKeepAlive()) writer.close();
            return end;
        }

        if (!method.equals("GET")) {
            writer.write(HttpResponse.methodNotAllowed().getBytes());
            if (!writer.isKeepAlive()) writer.close();
            return end;
        }

        if (path.equals("/")) path = "/index.html";

        // GET /playlists
        if (path.equals("/playlists")) {
            playlistController.list(writer);
            return end;
        }

        // GET /playlists/:id
        if (segments.length == 3
                && segments[1].equals("playlists")
                && isNumeric(segments[2])) {
            playlistController.get(writer, segments[2]);
            return end;
        }

        // GET /library
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

    private String readBody(String headers, byte[] data, int end) {
        int length = bodyLength(headers);
        if (length <= 0 || end >= data.length) return "";
        return new String(data, end,
                Math.min(length, data.length - end),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private int bodyLength(String headers) {
        String cl = HttpParser.extractHeader(headers, "Content-Length");
        if (cl == null) return 0;
        try { return Integer.parseInt(cl.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private boolean isNumeric(String s) {
        try { Integer.parseInt(s); return true; }
        catch (NumberFormatException e) { return false; }
    }
}