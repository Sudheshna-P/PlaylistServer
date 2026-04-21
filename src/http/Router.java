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
                        SocketChannel client, SelectionKey key,
                        String headers, byte[] data,
                        int end, boolean keepAlive) throws IOException {

        // POST /upload
        if (method.equals("POST") && path.equals("/upload")) {
            int alreadyBuffered = data.length - end;
            return uploadController.beginUpload(key, client, data,
                    headers, end, alreadyBuffered, keepAlive);
        }

        // POST /playlist/add
        if (method.equals("POST") && path.equals("/playlist/add")) {
            String contentLength = HttpParser.extractHeader(headers, "Content-Length");
            int bodyLength = contentLength != null
                    ? Integer.parseInt(contentLength.trim()) : 0;
            String body = new String(data, end, bodyLength, StandardCharsets.UTF_8);
            playlistAddController.handle(client, key, body, keepAlive);
            return end + bodyLength;
        }

        // DELETE /playlist/:name
        if (method.equals("DELETE") && path.startsWith("/playlist/")) {
            String filename = path.substring("/playlist/".length());
            playlistRemoveController.handle(client, key, filename, keepAlive);
            return end;
        }

        // DELETE /uploads/:name
        if (method.equals("DELETE") && path.startsWith("/uploads/")) {
            String filename = path.substring("/uploads/".length());
            deleteMediaController.handle(client, key, filename, keepAlive);
            return end;
        }

        // non GET methods
        if (!method.equals("GET")) {
            HttpResponse.send(client, key,
                    HttpResponse.methodNotAllowed().getBytes(), keepAlive);
            return end;
        }

        // GET routes
        if (path.equals("/")) path = "/index.html";

        if (path.equals("/playlist")) {
            playlistController.handle(client, key, keepAlive);
            return end;
        }

        if (path.equals("/uploads") || path.equals("/library")) {
            libraryController.handle(client, key, keepAlive);
            return end;
        }

        // static file serving
        Path filePath = PathResolver.resolve(path);
        if (filePath == null) {
            HttpResponse.send(client, key,
                    HttpResponse.notFound().getBytes(), keepAlive);
            return end;
        }
        if (!PathResolver.isSafe(filePath)) {
            HttpResponse.sendBytes(client,
                    HttpResponse.forbidden().getBytes());
            HttpResponse.cancelAndClose(key, client);
            return -1;
        }
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            HttpResponse.send(client, key,
                    HttpResponse.notFound().getBytes(), keepAlive);
            return end;
        }


        String rangeHeader = HttpParser.extractHeader(headers, "Range");
        fileController.handle(client, key, filePath, path, rangeHeader, keepAlive);
        return end;
    }
    public void continueUpload(SelectionKey key, SocketChannel client,
                               UploadController.UploadState state,
                               ByteBuffer buffer) throws IOException {
        uploadController.continueUpload(key, client, state, buffer);
    }


}