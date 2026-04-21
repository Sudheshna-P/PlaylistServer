import controller.*;
import http.HttpParser;
import http.HttpResponse;
import http.Router;
import model.LibraryModel;
import model.PlaylistModel;
import model.UploadModel;
import storage.PlaylistStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import logger.Logger;
import logger.LoggerManager;
import logger.LoggerFactory;

public class SimpleHttpServer {

    private static final int PORT = 8080;
    public static final String BASE    = "/home/sudheshna/IdeaProjects/PlaylistServer/src";
    public static final String UPLOADS = BASE + "/uploads";

    private final LoggerManager logger;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(8);
    private final Router router;

    public SimpleHttpServer() {
        // create directories
        try { Files.createDirectories(Paths.get(BASE + "/logs")); }
        catch (IOException e) { throw new RuntimeException("Cannot create logs dir", e); }
        try { Files.createDirectories(Paths.get(UPLOADS)); }
        catch (IOException e) { throw new RuntimeException("Cannot create uploads dir", e); }

        // logger
        Logger fileLogger = LoggerFactory.getFileLogger(BASE + "/logs/server.log");
        Logger consoleLogger = LoggerFactory.getConsoleLogger();
        this.logger = new LoggerManager(List.of(fileLogger, consoleLogger));

        // models and storage
        LibraryModel libraryModel    = new LibraryModel(UPLOADS);
        UploadModel uploadModel      = new UploadModel(UPLOADS);
        PlaylistStore playlistStore  = new PlaylistStore(BASE);
        PlaylistModel playlistModel  = new PlaylistModel(playlistStore, libraryModel);

        // controllers
        UploadController uploadController                 = new UploadController(UPLOADS, uploadModel, ioPool);
        LibraryController libraryController               = new LibraryController(libraryModel);
        PlaylistController playlistController             = new PlaylistController(playlistModel);
        PlaylistAddController playlistAddController       = new PlaylistAddController(playlistModel);
        PlaylistRemoveController playlistRemoveController = new PlaylistRemoveController(playlistModel);
        FileController fileController                     = new FileController(ioPool);
        DeleteMediaController deleteMediaController       = new DeleteMediaController(UPLOADS);

        // router
        this.router = new Router(
                uploadController,
                libraryController,
                playlistController,
                playlistAddController,
                playlistRemoveController,
                fileController,
                deleteMediaController
        );
    }

    private void handleClient(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        ByteBuffer buffer = ByteBuffer.allocate(65536);
        int bytesRead;
        try {
            bytesRead = client.read(buffer);
        } catch (IOException e) {
            HttpResponse.cancelAndClose(key, client); return;
        }
        if (bytesRead == -1) { HttpResponse.cancelAndClose(key, client); return; }
        buffer.flip();

        Object attachment = key.attachment();

        // upload in progress
        if (attachment instanceof UploadController.UploadState) {
            uploadController(key, client, attachment, buffer);
            return;
        }

        UploadController.ReadAcc acc = (attachment instanceof UploadController.ReadAcc)
                ? (UploadController.ReadAcc) attachment
                : new UploadController.ReadAcc();
        acc.buf.write(buffer.array(), 0, buffer.limit());
        key.attach(acc);

        byte[] data   = acc.buf.toByteArray();
        int processed = 0;

        while (true) {
            int headerEnd = HttpParser.findHeaderEnd(data, processed);
            if (headerEnd == -1) break;

            int end = headerEnd + 4;
            String headers = new String(data, processed, end - processed,
                    StandardCharsets.US_ASCII);
            String requestLine = headers.lines().findFirst().orElse("");

            if (requestLine.isEmpty()) { HttpResponse.cancelAndClose(key, client); return; }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2) { HttpResponse.cancelAndClose(key, client); return; }

            String method = parts[0];
            String path   = parts[1];
            int qIdx = path.indexOf('?');
            if (qIdx != -1) path = path.substring(0, qIdx);

            logger.info("Request: " + requestLine);

            String  connHdr   = HttpParser.extractHeader(headers, "Connection");
            boolean keepAlive = !"close".equalsIgnoreCase(connHdr);

            int result = router.dispatch(method, path, client, key,
                    headers, data, end, keepAlive);

            if (result == -1) return;
            processed = result;
            if (key.attachment() instanceof UploadController.UploadState) break;
        }

        if (!(key.attachment() instanceof UploadController.UploadState)) {
            UploadController.ReadAcc newAcc = new UploadController.ReadAcc();
            if (processed < data.length)
                newAcc.buf.write(data, processed, data.length - processed);
            key.attach(newAcc);
            if (key.isValid()) key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void uploadController(SelectionKey key, SocketChannel client,
                                  Object attachment, ByteBuffer buffer) throws IOException {
        // find the upload controller from router to continue upload
        router.continueUpload(key, client,
                (UploadController.UploadState) attachment, buffer);
    }

    public static void main(String[] args) {
        SimpleHttpServer server = new SimpleHttpServer();
        server.logger.info("Starting server on port " + PORT);
        new ServerNIO(PORT, server::handleClient).start();
    }
}