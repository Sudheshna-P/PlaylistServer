package controller;

import http.HttpParser;
import http.HttpResponse;
import http.ResponseWriter;
import logger.LoggerManager;
import service.UploadService;
import util.JsonWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

public class UploadController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH).withZone(ZoneOffset.UTC);

    private static final long MAX_UPLOAD_SIZE = 500L * 1024 * 1024;

    private final String uploadsPath;
    private final UploadService uploadService;
    private final ExecutorService ioPool;

    public UploadController(String uploadsPath, UploadService uploadService, ExecutorService ioPool) {
        this.uploadsPath   = uploadsPath;
        this.uploadService = uploadService;
        this.ioPool  = ioPool;
    }

    public Object beginUpload(ResponseWriter writer, String headers, byte[] data, int headerEnd,
                              int alreadyBuffered) throws IOException {

        String contentType = HttpParser.extractHeader(headers, "Content-Type");
        String contentLength = HttpParser.extractHeader(headers, "Content-Length");

        if (contentLength == null) {
            writer.write(HttpResponse.error(411, "Length Required",
                    "Content-Length missing").getBytes());
            return "error";
        }

        long bodyLength;
        try { bodyLength = Long.parseLong(contentLength.trim()); }
        catch (NumberFormatException e) {
            writer.write(HttpResponse.error(400, "Bad Request",
                    "Invalid Content-Length").getBytes());
            return "error";
        }

        if (bodyLength > MAX_UPLOAD_SIZE) {
            writer.write(HttpResponse.error(413, "Payload Too Large",
                    "Max " + (MAX_UPLOAD_SIZE / 1024 / 1024) + " MB").getBytes());
            return "error";
        }

        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            String boundary = HttpParser.extractBoundary(contentType);
            if (boundary == null) {
                writer.write(HttpResponse.error(400, "Bad Request", "Missing boundary").getBytes());
                return "error";
            }

            Path tempFile = Paths.get(uploadsPath,
                    "upload_" + System.currentTimeMillis() + ".tmp");
            FileChannel outChannel = FileChannel.open(tempFile,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            http.UploadState state = new http.UploadState(
                    tempFile, outChannel, bodyLength,
                    boundary, writer.isKeepAlive(), writer);

            if (alreadyBuffered > 0) {
                ByteBuffer bodyChunk = ByteBuffer.wrap(data, headerEnd, alreadyBuffered);
                while (bodyChunk.hasRemaining()) outChannel.write(bodyChunk);
                state.written += alreadyBuffered;
            }

            return state;
        }

        // non-multipart
        String body = new String(data, headerEnd, (int) bodyLength, StandardCharsets.UTF_8);
        String rb = "<h1>POST Received</h1>";
        String response = "HTTP/1.1 200 OK\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + rb.length() + "\r\n" +
                "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close")
                + "\r\n\r\n" + rb;
        writer.write(response.getBytes(StandardCharsets.UTF_8));
        return "done";
    }

    public boolean continueUpload(http.UploadState state,
                                  ByteBuffer chunk) throws IOException {
        long remaining = state.totalBodyBytes - state.written;
        if (chunk.limit() > remaining) chunk.limit((int) remaining);

        int writtenNow = chunk.remaining();
        while (chunk.hasRemaining()) state.out.write(chunk);
        state.written += writtenNow;

        return state.written >= state.totalBodyBytes;
    }

    public void finishUpload(http.UploadState state,
                             Runnable onDone) {
        try { state.out.close(); } catch (IOException ignored) {}

        ioPool.submit(() -> {
            try {
                byte[] body = Files.readAllBytes(state.tempFile);
                Files.delete(state.tempFile);

                List<String> saved  = new ArrayList<>();
                List<String> errors = new ArrayList<>();
                uploadService.parse(body, state.boundary, saved, errors);
                sendUploadResponse(state.writer, saved, errors);
            } catch (Exception e) {
                LoggerManager.getInstance().info(
                        "[ERROR] UploadController.finishUpload: " + e.getMessage());
            } finally {
                onDone.run(); // SimpleHttpServer resets key state
            }
        });
    }

    private void sendUploadResponse(ResponseWriter writer,
                                    List<String> saved, List<String> errors) {
        List<String> savedJson  = new ArrayList<>();
        List<String> errorsJson = new ArrayList<>();
        for (String s : saved)  savedJson.add("\"" + JsonWriter.escape(s) + "\"");
        for (String e : errors) errorsJson.add("\"" + JsonWriter.escape(e) + "\"");

        String bodyStr = "{\n  \"uploaded\": [" + String.join(", ", savedJson) +
                "],\n  \"errors\": [" + String.join(", ", errorsJson) + "]\n}";

        int status = errors.isEmpty() ? 200 : saved.isEmpty() ? 400 : 207;
        String statusText = status == 200 ? "OK" :
                status == 207 ? "Multi-Status" : "Bad Request";

        String response = "HTTP/1.1 " + status + " " + statusText + "\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Content-Type: application/json\r\n" +
                "Content-Length: " + bodyStr.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close")
                + "\r\n\r\n" + bodyStr;
        try {
            writer.write(response.getBytes(StandardCharsets.UTF_8));
            if (!writer.isKeepAlive()) writer.close();
        } catch (IOException e) {
            LoggerManager.getInstance().info(
                    "[ERROR] UploadController.sendResponse: " + e.getMessage());
        }
    }
}