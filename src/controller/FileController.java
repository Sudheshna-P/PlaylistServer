package controller;

import http.HttpResponse;
import util.ContentType;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
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

    public void handle(SocketChannel client, SelectionKey key,
                       Path filePath, String path,
                       String rangeHeader, boolean keepAlive) {

        key.interestOps(0);

        if (rangeHeader != null && rangeHeader.trim().toLowerCase().startsWith("bytes=")) {
            ioPool.submit(() -> {
                try { serveRange(client, key, filePath, path, rangeHeader, keepAlive); }
                catch (IOException e) { try { client.close(); } catch (IOException ignored) {} }
                finally { if (key.isValid()) { key.interestOps(SelectionKey.OP_READ); key.selector().wakeup(); } }
            });
        } else {
            ioPool.submit(() -> {
                try { serveFullFile(client, filePath, keepAlive, path); }
                catch (IOException e) { try { client.close(); } catch (IOException ignored) {} }
                finally { if (key.isValid()) { key.interestOps(SelectionKey.OP_READ); key.selector().wakeup(); } }
            });
        }
    }

    private void serveFullFile(SocketChannel client, Path filePath,
                               boolean keepAlive, String path) throws IOException {
        long fileSize = Files.size(filePath);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Last-Modified: " + HTTP_DATE.format(Files.getLastModifiedTime(filePath).toInstant()) + "\r\n" +
                "Content-Type: " + ContentType.get(path) + "\r\n" +
                "Content-Length: " + fileSize + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n";
        client.write(ByteBuffer.wrap(header.getBytes(StandardCharsets.US_ASCII)));
        try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long pos = 0, rem = fileSize;
            while (rem > 0) { long s = fc.transferTo(pos, rem, client); if (s <= 0) break; pos += s; rem -= s; }
        }
        if (!keepAlive) client.close();
    }

    private void serveRange(SocketChannel client, SelectionKey key,
                            Path filePath, String path,
                            String rangeHeader, boolean keepAlive) throws IOException {
        long fileSize = Files.size(filePath);
        long start = 0, endByte = fileSize - 1;
        try {
            String[] parts = rangeHeader.substring(6).trim().split("-", 2);
            if (!parts[0].isEmpty()) start   = Long.parseLong(parts[0].trim());
            if (parts.length > 1 && !parts[1].isEmpty()) endByte = Long.parseLong(parts[1].trim());
        } catch (Exception ignored) {}

        if (start > endByte || start >= fileSize) {
            HttpResponse.send(client, key,
                    ("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */"
                            + fileSize + "\r\nContent-Length: 0\r\n\r\n").getBytes(), keepAlive);
            return;
        }

        endByte = Math.min(endByte, fileSize - 1);
        long contentLength = endByte - start + 1;

        String header = "HTTP/1.1 206 Partial Content\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Last-Modified: " + HTTP_DATE.format(Files.getLastModifiedTime(filePath).toInstant()) + "\r\n" +
                "Content-Type: " + ContentType.get(path) + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Content-Range: bytes " + start + "-" + endByte + "/" + fileSize + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n";

        client.write(ByteBuffer.wrap(header.getBytes(StandardCharsets.US_ASCII)));
        try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
            long pos = start, rem = contentLength;
            while (rem > 0) { long s = fc.transferTo(pos, rem, client); if (s <= 0) break; pos += s; rem -= s; }
        }
        if (!keepAlive) client.close();
    }
}