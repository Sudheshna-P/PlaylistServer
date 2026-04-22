package controller;

import http.HttpResponse;
import http.ResponseWriter;
import util.ContentType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public class FileController {

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final ExecutorService ioPool;

    public FileController(ExecutorService ioPool) {
        this.ioPool = ioPool;
    }

    public void handle(ResponseWriter writer, Path filePath,
                       String path, String rangeHeader) {

        writer.pauseReads();

        if (rangeHeader != null && rangeHeader.trim().toLowerCase().startsWith("bytes=")) {
            ioPool.submit(() -> {
                try { serveRange(writer, filePath, path, rangeHeader); }
                catch (IOException e) { try { writer.close(); } catch (Exception ignored) {} }
                finally { writer.resumeReads(); }
            });
        } else {
            ioPool.submit(() -> {
                try { serveFullFile(writer, filePath, path); }
                catch (IOException e) { try { writer.close(); } catch (Exception ignored) {} }
                finally { writer.resumeReads(); }
            });
        }
    }

    private void serveFullFile(ResponseWriter writer, Path filePath,
                               String path) throws IOException {
        long fileSize = Files.size(filePath);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Last-Modified: " + HTTP_DATE.format(
                Files.getLastModifiedTime(filePath).toInstant()) + "\r\n" +
                "Content-Type: " + ContentType.get(path) + "\r\n" +
                "Content-Length: " + fileSize + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n";

        writer.write(header.getBytes(StandardCharsets.US_ASCII));
        try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long pos = 0, rem = fileSize;
            while (rem > 0) {
                long s = writer.transferFrom(fc, pos, rem);
                if (s <= 0) break;
                pos += s; rem -= s;
            }
        }
        if (!writer.isKeepAlive()) writer.close();
    }

    private void serveRange(ResponseWriter writer, Path filePath,
                            String path, String rangeHeader) throws IOException {
        long fileSize = Files.size(filePath);
        long start = 0, endByte = fileSize - 1;
        try {
            String[] parts = rangeHeader.substring(6).trim().split("-", 2);
            if (!parts[0].isEmpty()) start   = Long.parseLong(parts[0].trim());
            if (parts.length > 1 && !parts[1].isEmpty())
                endByte = Long.parseLong(parts[1].trim());
        } catch (Exception ignored) {}

        if (start > endByte || start >= fileSize) {
            writer.write(("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"
                    + fileSize + "\r\nContent-Length: 0\r\n\r\n").getBytes());
            return;
        }

        endByte = Math.min(endByte, fileSize - 1);
        long contentLength = endByte - start + 1;

        String header = "HTTP/1.1 206 Partial Content\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Last-Modified: " + HTTP_DATE.format(
                Files.getLastModifiedTime(filePath).toInstant()) + "\r\n" +
                "Content-Type: " + ContentType.get(path) + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Content-Range: bytes " + start + "-" + endByte + "/" + fileSize + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: " + (writer.isKeepAlive() ? "keep-alive" : "close") + "\r\n\r\n";

        writer.write(header.getBytes(StandardCharsets.US_ASCII));
        try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long pos = start, rem = contentLength;
            while (rem > 0) {
                long s = writer.transferFrom(fc, pos, rem);
                if (s <= 0) break;
                pos += s; rem -= s;
            }
        }
        if (!writer.isKeepAlive()) writer.close();
    }
}