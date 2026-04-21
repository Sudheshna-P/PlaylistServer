package controller;

import http.HttpParser;
import http.HttpResponse;
import model.UploadModel;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.nio.file.Files;

public class UploadController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private static final long MAX_UPLOAD_SIZE = 500L * 1024 * 1024;

    private final String uploadsPath;
    private final UploadModel uploadModel;
    private final ExecutorService ioPool;

    public static class UploadState {
        public final Path tempFile;
        public final FileChannel out;
        public final long totalBodyBytes;
        public long written = 0;
        public final String boundary;
        public final boolean keepAlive;

        public UploadState(Path tempFile, FileChannel out,
                           long totalBodyBytes, String boundary, boolean keepAlive) {
            this.tempFile = tempFile;
            this.out = out;
            this.totalBodyBytes = totalBodyBytes;
            this.boundary = boundary;
            this.keepAlive = keepAlive;
        }
    }

    public UploadController(String uploadsPath, UploadModel uploadModel,
                            ExecutorService ioPool) {
        this.uploadsPath = uploadsPath;
        this.uploadModel = uploadModel;
        this.ioPool = ioPool;
    }

    public int beginUpload(SelectionKey key, SocketChannel client,
                           byte[] data, String headers, int headerEnd,
                           int alreadyBuffered, boolean keepAlive) throws IOException {

        String contentType   = HttpParser.extractHeader(headers, "Content-Type");
        String contentLength = HttpParser.extractHeader(headers, "Content-Length");

        if (contentLength == null) {
            HttpResponse.send(client, key,
                    HttpResponse.error(411, "Length Required",
                            "Content-Length missing").getBytes(), keepAlive);
            return headerEnd;
        }

        long bodyLength = Long.parseLong(contentLength.trim());

        if (bodyLength > MAX_UPLOAD_SIZE) {
            HttpResponse.send(client, key,
                    HttpResponse.error(413, "Payload Too Large",
                            "Max " + (MAX_UPLOAD_SIZE / 1024 / 1024) + " MB").getBytes(), keepAlive);
            return headerEnd;
        }

        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            String boundary = HttpParser.extractBoundary(contentType);
            if (boundary == null) {
                HttpResponse.send(client, key,
                        HttpResponse.error(400, "Bad Request",
                                "Missing boundary").getBytes(), keepAlive);
                return headerEnd;
            }

            Path tempFile = Paths.get(uploadsPath,
                    "upload_" + System.currentTimeMillis() + ".tmp");
            FileChannel outChannel = FileChannel.open(tempFile,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            UploadState state = new UploadState(tempFile, outChannel,
                    bodyLength, boundary, keepAlive);

            if (alreadyBuffered > 0) {
                ByteBuffer bodyChunk = ByteBuffer.wrap(data, headerEnd, alreadyBuffered);
                while (bodyChunk.hasRemaining()) outChannel.write(bodyChunk);
                state.written += alreadyBuffered;
            }

            key.attach(state);

            if (state.written >= state.totalBodyBytes) {
                finishUpload(key, client, state);
            }

            return -2;
        }

        // non-multipart POST
        String body = new String(data, headerEnd, (int) bodyLength, StandardCharsets.UTF_8);
        String rb = "<h1>POST Received</h1>";
        String response = "HTTP/1.1 200 OK\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + rb.length() + "\r\n" +
                "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n" + rb;
        client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
        if (!keepAlive) { HttpResponse.cancelAndClose(key, client); return -1; }
        return (int)(headerEnd + bodyLength);
    }

    public void continueUpload(SelectionKey key, SocketChannel client,
                               UploadState state, ByteBuffer chunk) throws IOException {
        long remaining = state.totalBodyBytes - state.written;
        if (chunk.limit() > remaining) chunk.limit((int) remaining);

        int writtenNow = chunk.remaining();
        while (chunk.hasRemaining()) {
            state.out.write(chunk);
        }
        state.written += writtenNow;

        if (state.written >= state.totalBodyBytes) {
            finishUpload(key, client, state);
        }
    }

    public void finishUpload(SelectionKey key, SocketChannel client,
                             UploadState state) throws IOException {
        try { state.out.close(); } catch (IOException ignored) {}

        key.interestOps(0);
        ioPool.submit(() -> {
            try {
                byte[] body = Files.readAllBytes(state.tempFile);
                Files.delete(state.tempFile);

                List<String> saved  = new ArrayList<>();
                List<String> errors = new ArrayList<>();
                uploadModel.parse(body, state.boundary, saved, errors);
                sendUploadResponse(client, key, saved, errors, state.keepAlive);
            } catch (Exception e) {
                try { client.close(); } catch (IOException ignored) {}
            } finally {
                key.attach(new ReadAcc());
                if (key.isValid()) {
                    key.interestOps(SelectionKey.OP_READ);
                    key.selector().wakeup();
                }
            }
        });
    }

    private void sendUploadResponse(SocketChannel client, SelectionKey key,
                                    List<String> saved, List<String> errors,
                                    boolean keepAlive) {
        StringBuilder body = new StringBuilder();
        body.append("{\n  \"uploaded\": [");
        for (int i = 0; i < saved.size(); i++) {
            body.append("\"").append(saved.get(i)).append("\"");
            if (i < saved.size() - 1) body.append(", ");
        }
        body.append("],\n  \"errors\": [");
        for (int i = 0; i < errors.size(); i++) {
            body.append("\"").append(errors.get(i)).append("\"");
            if (i < errors.size() - 1) body.append(", ");
        }
        body.append("]\n}");

        String bodyStr    = body.toString();
        int    status     = errors.isEmpty() ? 200 : saved.isEmpty() ? 400 : 207;
        String statusText = status == 200 ? "OK" : status == 207 ? "Multi-Status" : "Bad Request";

        String response = "HTTP/1.1 " + status + " " + statusText + "\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + bodyStr.length() + "\r\n" +
                "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n" +
                bodyStr;
        try {
            client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
            if (!keepAlive) HttpResponse.cancelAndClose(key, client);
        } catch (IOException e) {
            try { client.close(); } catch (IOException ignored) {}
        }
    }

    // ReadAcc needed here for finishUpload finally block
    public static class ReadAcc {
        public final java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
    }
}