package http;

import controller.*;
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
    private final PlaylistAddController playlistAddController;
    private final PlaylistRemoveController playlistRemoveController;
    private final FileController fileController;
    private final DeleteMediaController deleteMediaController;

    public Router(UploadController uploadController,
                  LibraryController libraryController,
                  PlaylistController playlistController,
                  PlaylistAddController playlistAddController,
                  PlaylistRemoveController playlistRemoveController,
                  FileController fileController,
                  DeleteMediaController deleteMediaController) {
        this.uploadController         = uploadController;
        this.libraryController        = libraryController;
        this.playlistController       = playlistController;
        this.playlistAddController    = playlistAddController;
        this.playlistRemoveController = playlistRemoveController;
        this.fileController           = fileController;
        this.deleteMediaController    = deleteMediaController;
    }

    public int dispatch(String method, String path,
                        ResponseWriter writer,
                        String headers, byte[] data,
                        int end) throws IOException {

        // POST /upload
        if (method.equals("POST") && path.equals("/upload")) {
            int alreadyBuffered = data.length - end;
            int result = uploadController.beginUpload(
                    writer.getKey(), writer.getClient(),
                    data, headers, end, alreadyBuffered, writer.isKeepAlive());
            return result;
        }

        // POST /playlist/add
        if (method.equals("POST") && path.equals("/playlist/add")) {
            String contentLength = HttpParser.extractHeader(headers, "Content-Length");
            int bodyLength = contentLength != null
                    ? Integer.parseInt(contentLength.trim()) : 0;
            String body = new String(data, end, bodyLength, StandardCharsets.UTF_8);
            playlistAddController.handle(writer, body);
            return end + bodyLength;
        }

        // DELETE /playlist/:name
        if (method.equals("DELETE") && path.startsWith("/playlist/")) {
            String filename = path.substring("/playlist/".length());
            playlistRemoveController.handle(writer, filename);
            return end;
        }

        // DELETE /uploads/:name
        if (method.equals("DELETE") && path.startsWith("/uploads/")) {
            String filename = path.substring("/uploads/".length());
            deleteMediaController.handle(writer, filename);
            return end;
        }

        // non GET methods
        if (!method.equals("GET")) {
            writer.write(HttpResponse.methodNotAllowed().getBytes());
            if (!writer.isKeepAlive()) writer.close();
            return end;
        }

        // GET routes
        if (path.equals("/")) path = "/index.html";

        if (path.equals("/playlist")) {
            playlistController.handle(writer);
            return end;
        }

        if (path.equals("/uploads") || path.equals("/library")) {
            libraryController.handle(writer);
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
                               java.nio.ByteBuffer buffer) throws IOException {
        uploadController.continueUpload(key, client, state, buffer);
    }
}