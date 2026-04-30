import controller.*;
import http.*;
import model.LibraryModel;
import model.UploadModel;
import service.LibraryService;
import service.PlaylistService;
import service.UploadService;
import storage.Database;
import storage.PlaylistStoreDB;

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
    public static final String BASE = "/home/sudheshna/IdeaProjects/PlaylistServer/src";
    public static final String UPLOADS = BASE + "/uploads";

    private final LoggerManager logger;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(8);
    private final Router router;
    private final UploadController uploadController;

    public SimpleHttpServer() {
        try { Files.createDirectories(Paths.get(BASE + "/logs")); }
        catch (IOException e) { throw new RuntimeException("Cannot create logs dir", e); }
        try { Files.createDirectories(Paths.get(UPLOADS)); }
        catch (IOException e) { throw new RuntimeException("Cannot create uploads dir", e); }

        Logger fileLogger = LoggerFactory.getFileLogger(BASE + "/logs/server.log");
        Logger consoleLogger = LoggerFactory.getConsoleLogger();
        this.logger = new LoggerManager(List.of(fileLogger, consoleLogger));
        LoggerManager.setInstance(this.logger);

        // models
        LibraryModel libraryModel = new LibraryModel(UPLOADS);
        UploadModel uploadModel = new UploadModel(UPLOADS);

        // storage
        Database db;
        try { db = new Database(BASE); }
        catch (Exception e) { throw new RuntimeException("Cannot init database", e); }
        PlaylistStoreDB playlistStoreDB = new PlaylistStoreDB(db);

        // services
        LibraryService libraryService = new LibraryService(libraryModel);
        PlaylistService playlistService = new PlaylistService(playlistStoreDB);
        UploadService uploadService = new UploadService(uploadModel);

        // controllers
        this.uploadController = new UploadController(UPLOADS, uploadService, ioPool);
        LibraryController libraryController = new LibraryController(libraryService);
        PlaylistController playlistController = new PlaylistController(playlistService);
        FileController fileController = new FileController(ioPool);

        // router
        this.router = new Router(
                libraryController,
                playlistController,
                fileController
        );
    }

    private void handleClient(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        ByteBuffer buffer = ByteBuffer.allocate(65536);
        int bytesRead;
        try { bytesRead = client.read(buffer); }
        catch (IOException e) { HttpResponse.cancelAndClose(key, client); return; }
        if (bytesRead == -1) { HttpResponse.cancelAndClose(key, client); return; }
        buffer.flip();

        Object attachment = key.attachment();

        if (attachment instanceof UploadState) {
            UploadState state = (UploadState) attachment;
            boolean done = uploadController(state, buffer);
            if (done) {
                key.interestOps(0); // pause reads while processing
                uploadController.finishUpload(state, () -> {
                    key.attach(new ReadAcc());
                    if (key.isValid()) {
                        key.interestOps(SelectionKey.OP_READ);
                        key.selector().wakeup();
                    }
                });
            }
            return;
        }

        ReadAcc acc = (attachment instanceof ReadAcc) ? (ReadAcc) attachment : new ReadAcc();
        acc.buf.write(buffer.array(), 0, buffer.limit());

        if (acc.buf.size() > 8192) {
            try {
                client.write(ByteBuffer.wrap(HttpResponse.error(431,
                        "Request Header Fields Too Large",
                        "Headers too big").getBytes()));
            } catch (IOException ignored) {}
            try { client.close(); } catch (IOException ignored) {}
            return;
        }

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

            ResponseWriter writer = new ResponseWriter(key, client, keepAlive);

            // handle upload specially since it needs key attachment
            if (method.equals("POST") && path.equals("/upload")) {
                int alreadyBuffered = data.length - end;
                Object result = uploadController.beginUpload(writer, headers, data, end, alreadyBuffered);

                if (result instanceof UploadState) {
                    UploadState state = (UploadState) result;
                    key.attach(state);
                    if (state.written >= state.totalBodyBytes) {
                        key.interestOps(0);
                        uploadController.finishUpload(state, () -> {
                            key.attach(new ReadAcc());
                            if (key.isValid()) {
                                key.interestOps(SelectionKey.OP_READ);
                                key.selector().wakeup();
                            }
                        });
                    }
                    break;
                }
                if ("error".equals(result)) {
                    if (!keepAlive) HttpResponse.cancelAndClose(key, client);
                }
                processed = end;
                continue;
            }

            int result = router.dispatch(method, path, writer, headers, data, end);
            if (result == -1) return;
            processed = result;
        }

        if (!(key.attachment() instanceof UploadState)) {
            ReadAcc newAcc = new ReadAcc();
            if (processed < data.length)
                newAcc.buf.write(data, processed, data.length - processed);
            key.attach(newAcc);
            if (key.isValid()) key.interestOps(SelectionKey.OP_READ);
        }
    }

    private boolean uploadController(UploadState state, ByteBuffer buffer)
            throws IOException {
        return uploadController.continueUpload(state, buffer);
    }


    public static void main(String[] args) {
        SimpleHttpServer server = new SimpleHttpServer();
        server.logger.info("Starting server on port " + PORT);
        new ServerNIO(PORT, server::handleClient).start();
    }
}