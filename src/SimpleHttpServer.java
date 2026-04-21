import logger.Logger;
import logger.LoggerFactory;
import logger.LoggerManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SimpleHttpServer {

    private static final int PORT = 8080;
    private static final String BASE    = "/home/sudheshna/IdeaProjects/PlaylistServer/src";
    private static final String UPLOADS = BASE + "/uploads";

    private static final long MAX_UPLOAD_SIZE = 500L * 1024 * 1024; // 500 MB

    private static final DateTimeFormatter HTTP_DATE =
            DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    private final LoggerManager logger;
    private final ExecutorService ioPool = Executors.newFixedThreadPool(8);

    /** accumulates request headers */
    private static class ReadAcc {
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
    }

    /** Upload in progress: streaming body chunks to a temp file */
    private static class UploadState {
        final Path tempFile;
        final FileChannel out;
        final long totalBodyBytes;
        long written = 0;
        final String boundary;
        final boolean keepAlive;

        UploadState(Path tempFile, FileChannel out,
                    long totalBodyBytes, String boundary, boolean keepAlive) {
            this.tempFile = tempFile;
            this.out  = out;
            this.totalBodyBytes = totalBodyBytes;
            this.boundary = boundary;
            this.keepAlive = keepAlive;
        }
    }

    public SimpleHttpServer() {
        Logger fileLogger = LoggerFactory.getFileLogger(BASE + "/logs/server.log");
        Logger consoleLogger = LoggerFactory.getConsoleLogger();
        this.logger = new LoggerManager(List.of(fileLogger, consoleLogger));
        try { Files.createDirectories(Paths.get(UPLOADS)); }
        catch (IOException e) { throw new RuntimeException("Cannot create uploads dir", e); }
    }

    private void handleClient(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();

        ByteBuffer buffer = ByteBuffer.allocate(65536); // 64 KB read buffer
        int bytesRead;
        try {
            bytesRead = client.read(buffer);
        } catch (IOException e) {
            cancelAndClose(key, client); return;
        }
        if (bytesRead == -1) { cancelAndClose(key, client); return; }
        buffer.flip();

        Object attachment = key.attachment();

        if (attachment instanceof UploadState) {
            continueUpload(key, client, (UploadState) attachment, buffer);
            return;
        }

        ReadAcc acc = (attachment instanceof ReadAcc) ? (ReadAcc) attachment : new ReadAcc();
        acc.buf.write(buffer.array(), 0, buffer.limit());
        key.attach(acc);

        byte[] data     = acc.buf.toByteArray();
        int    processed = 0;

        while (true) {
            int headerEnd = findHeaderEnd(data, processed);
            if (headerEnd == -1) break;

            int end = headerEnd + 4;
            String headers = new String(data, processed, end - processed, StandardCharsets.US_ASCII);
            String requestLine = headers.lines().findFirst().orElse("");

            if (requestLine.isEmpty()) { cancelAndClose(key, client); return; }

            String[] parts = requestLine.split(" ");
            if (parts.length < 2)    { cancelAndClose(key, client); return; }

            String method = parts[0];
            String path   = parts[1];
            int qIdx = path.indexOf('?');
            if (qIdx != -1) path = path.substring(0, qIdx);

            logger.info("Request: " + requestLine);

            String  connHdr   = extractHeader(headers, "Connection");
            boolean keepAlive = !"close".equalsIgnoreCase(connHdr);

            if (method.equals("POST")) {
                int alreadyBuffered = data.length - end;

                int result = beginPost(key, client, data, headers,
                        end, alreadyBuffered, keepAlive);
                if (result == -1) return;
                processed = result;
                if (key.attachment() instanceof UploadState) break;
                continue;
            }

            if (!method.equals("GET")) {
                sendResponse(client, key, send405().getBytes(), keepAlive);
                processed = end;
                continue;
            }

            processed = handleGet(client, key, path, headers, keepAlive, end);
            if (processed == -1) return;
        }

        if (!(key.attachment() instanceof UploadState)) {
            ReadAcc newAcc = new ReadAcc();
            if (processed < data.length)
                newAcc.buf.write(data, processed, data.length - processed);
            key.attach(newAcc);
            if (key.isValid()) key.interestOps(SelectionKey.OP_READ);
        }
    }

    /**
     * Called once when the request headers are fully received.
     * For multipart uploads we immediately switch to streaming mode.
     * Returns -1 if connection was closed, otherwise the new processed offset.
     */
    private int beginPost(SelectionKey key, SocketChannel client,
                          byte[] data, String headers, int headerEnd,
                          int alreadyBuffered, boolean keepAlive) throws IOException {

        String contentType   = extractHeader(headers, "Content-Type");
        String contentLength = extractHeader(headers, "Content-Length");

        if (contentLength == null) {
            sendResponse(client, key,
                    sendError(411, "Length Required", "Content-Length missing").getBytes(), keepAlive);
            return headerEnd;
        }

        long bodyLength = Long.parseLong(contentLength.trim());

        if (bodyLength > MAX_UPLOAD_SIZE) {
            sendResponse(client, key,
                    sendError(413, "Payload Too Large",
                            "Max " + (MAX_UPLOAD_SIZE / 1024 / 1024) + " MB").getBytes(), keepAlive);
            return headerEnd;
        }

        if (contentType != null && contentType.toLowerCase().contains("multipart/form-data")) {
            String boundary = extractBoundary(contentType);
            if (boundary == null) {
                sendResponse(client, key,
                        sendError(400, "Bad Request", "Missing boundary").getBytes(), keepAlive);
                return headerEnd;
            }

            // Open a temp file to stream into
            Path tempFile = Paths.get(UPLOADS, "upload_" + System.currentTimeMillis() + ".tmp");
            FileChannel outChannel = FileChannel.open(tempFile,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            UploadState state = new UploadState(tempFile, outChannel,
                    bodyLength, boundary, keepAlive);

            if (alreadyBuffered > 0) {
                ByteBuffer bodyChunk = ByteBuffer.wrap(data, headerEnd, alreadyBuffered);
                while (bodyChunk.hasRemaining()) outChannel.write(bodyChunk);
                state.written += alreadyBuffered;
                logger.info("Upload started: " + bodyLength + " bytes total, "
                        + alreadyBuffered + " already buffered");
            }

            key.attach(state);

            if (state.written >= state.totalBodyBytes) {
                finishUpload(key, client, state);
            }

            return -2;
        }

        if (data.length < headerEnd + bodyLength) return 0;

        String body = new String(data, headerEnd, (int) bodyLength, StandardCharsets.UTF_8);
        logger.info("POST Body: " + body);

        String rb = "<h1>POST Received</h1>";
        String response = "HTTP/1.1 200 OK\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + rb.length() + "\r\n" +
                "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n" + rb;
        client.write(ByteBuffer.wrap(response.getBytes(StandardCharsets.UTF_8)));
        if (!keepAlive) { cancelAndClose(key, client); return -1; }
        return (int)(headerEnd + bodyLength);
    }

    /**
     * Called on every subsequent read event while an upload is in progress.
     * Writes the chunk straight to the temp file ? no buffering in memory.
     */
    private void continueUpload(SelectionKey key, SocketChannel client,
                                UploadState state, ByteBuffer chunk) throws IOException {
        long remaining = state.totalBodyBytes - state.written;
        if (chunk.limit() > remaining) chunk.limit((int) remaining);

        int writtenNow = chunk.remaining();
        while (chunk.hasRemaining()) {
            state.out.write(chunk);
        }
        state.written += writtenNow;

        logger.info("Upload progress: " + state.written + " / " + state.totalBodyBytes + " bytes");

        if (state.written >= state.totalBodyBytes) {
            finishUpload(key, client, state);
        }
    }

    //Parse temp file and respond

    private void finishUpload(SelectionKey key, SocketChannel client,
                              UploadState state) throws IOException {
        try {
            state.out.close();
        } catch (IOException ignored) {}

        logger.info("Upload complete, parsing multipart...");

        key.interestOps(0);
        ioPool.submit(() -> {
            try {
                byte[] body = Files.readAllBytes(state.tempFile);
                Files.delete(state.tempFile);

                List<String> saved  = new ArrayList<>();
                List<String> errors = new ArrayList<>();
                parseMultipart(body, state.boundary, saved, errors);
                sendUploadResponse(client, key, saved, errors, state.keepAlive);
            } catch (Exception e) {
                logger.info("finishUpload error: " + e.getMessage());
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

    private void parseMultipart(byte[] body, String boundary,
                                List<String> saved, List<String> errors) {

        byte[] delimiter     = ("\r\n--" + boundary).getBytes(StandardCharsets.US_ASCII);
        byte[] firstBoundary = ("--"     + boundary).getBytes(StandardCharsets.US_ASCII);

        int pos = indexOf(body, firstBoundary, 0);
        if (pos == -1) { errors.add("Malformed multipart body"); return; }
        pos += firstBoundary.length;

        while (pos < body.length) {
            // "--" means final boundary
            if (pos + 1 < body.length && body[pos] == '-' && body[pos+1] == '-') break;

            // skip \r\n after boundary
            if (pos + 1 < body.length && body[pos] == '\r' && body[pos+1] == '\n') pos += 2;
            else break;

            // find end of part headers
            int partHeaderEnd = indexOf(body, new byte[]{'\r','\n','\r','\n'}, pos);
            if (partHeaderEnd == -1) break;

            String partHeaders = new String(body, pos, partHeaderEnd - pos, StandardCharsets.US_ASCII);
            int dataStart = partHeaderEnd + 4;

            int dataEnd = indexOf(body, delimiter, dataStart);
            if (dataEnd == -1) dataEnd = body.length;

            String disposition = extractPartHeader(partHeaders, "Content-Disposition");
            String filename    = extractFilename(disposition);

            if (filename == null || filename.isEmpty()) {
                pos = dataEnd + delimiter.length;
                continue; // plain text field, skip
            }

            filename = Paths.get(filename).getFileName().toString()
                    .replaceAll("[^a-zA-Z0-9._\\-]", "_");

            Path savePath = Paths.get(UPLOADS, filename);
            if (Files.exists(savePath)) {
                String ts  = String.valueOf(System.currentTimeMillis());
                String ext = filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.')) : "";
                String base = filename.contains(".")
                        ? filename.substring(0, filename.lastIndexOf('.')) : filename;
                filename = base + "_" + ts + ext;
                savePath = Paths.get(UPLOADS, filename);
            }

            try {
                Files.write(savePath,
                        Arrays.copyOfRange(body, dataStart, dataEnd),
                        StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                saved.add(filename);
                logger.info("Saved: " + savePath + " (" + (dataEnd - dataStart) + " bytes)");
            } catch (IOException e) {
                errors.add(filename + " (" + e.getMessage() + ")");
                logger.info("Save failed: " + e.getMessage());
            }

            pos = dataEnd + delimiter.length;
        }
    }

    // Upload response
    private void sendUploadResponse(SocketChannel client, SelectionKey key,
                                    List<String> saved, List<String> errors,
                                    boolean keepAlive) {
        StringBuilder body = new StringBuilder();
        body.append("{\n  \"uploaded\": [");
        for (int i = 0; i < saved.size(); i++) {
            body.append("\"").append(saved.get(i)).append("\"");
            if (i < saved.size()-1) body.append(", ");
        }
        body.append("],\n  \"errors\": [");
        for (int i = 0; i < errors.size(); i++) {
            body.append("\"").append(errors.get(i)).append("\"");
            if (i < errors.size()-1) body.append(", ");
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
            if (!keepAlive) cancelAndClose(key, client);
        } catch (IOException e) {
            logger.info("Response write error: " + e.getMessage());
        }
    }

    private void handlePlaylist(SocketChannel client, SelectionKey key, boolean keepAlive) {
        try {
            List<String> entries = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(UPLOADS))) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) continue;
                    String name = p.getFileName().toString();
                    // skip temp files still being written
                    if (name.endsWith(".tmp")) continue;

                    String lower = name.toLowerCase(Locale.ROOT);
                    String type;
                    if (lower.endsWith(".mp4") || lower.endsWith(".webm") || lower.endsWith(".ogg")
                            || lower.endsWith(".mov") || lower.endsWith(".avi")) {
                        type = "video";
                    } else {
                        type = "image";
                    }
                    // build JSON object per file
                    entries.add("{\"name\":\"" + name.replace("\"", "\\\"")
                            + "\",\"type\":\"" + type + "\"}");
                }
            }
            String body = "[" + String.join(",", entries) + "]";
            String response = "HTTP/1.1 200 OK\r\n" +
                    "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                    "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n" +
                    body;
            sendResponse(client, key,
                    response.getBytes(StandardCharsets.UTF_8), keepAlive);
        } catch (IOException e) {
            logger.info("Playlist error: " + e.getMessage());
            sendResponse(client, key, send404().getBytes(), keepAlive);
        }
    }

    private int handleGet(SocketChannel client, SelectionKey key, String path,
                          String headers, boolean keepAlive, int end) throws IOException {

        if (path.equals("/")) path = "/index.html";
        // At the top of handleGet, before resolvePath:
        if (path.equals("/playlist")) {
            handlePlaylist(client, key, keepAlive);
            return end;
        }

        Path filePath = resolvePath(path);
        if (filePath == null) {
            sendResponse(client, key, send404().getBytes(), keepAlive); return end;
        }
        if (!isSafePath(filePath)) {
            sendBytes(client, send403().getBytes()); cancelAndClose(key, client); return -1;
        }
        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendResponse(client, key, send404().getBytes(), keepAlive); return end;
        }
        if (handleCaching(client, key, headers, filePath, keepAlive)) return end;

        String rangeHeader = extractHeader(headers, "Range");
        final String fp = path;
        key.interestOps(0);
        if (rangeHeader != null && rangeHeader.trim().toLowerCase().startsWith("bytes=")) {
            ioPool.submit(() -> {
                try { handleRange(client, key, headers, filePath, keepAlive, fp); }
                catch (IOException e) { logger.info("Range: " + e.getMessage()); try { client.close(); } catch (IOException ignored) {} }
                finally { if (key.isValid()) { key.interestOps(SelectionKey.OP_READ); key.selector().wakeup(); } }
            });
        } else {
            ioPool.submit(() -> {
                try { serveFullFile(client, filePath, keepAlive, fp); }
                catch (IOException e) { logger.info("Serve: " + e.getMessage()); try { client.close(); } catch (IOException ignored) {} }
                finally { if (key.isValid()) { key.interestOps(SelectionKey.OP_READ); key.selector().wakeup(); } }
            });
        }
        return end;
    }

    private void handleRange(SocketChannel client, SelectionKey key, String headers,
                             Path filePath, boolean keepAlive, String path) throws IOException {
        String rangeHeader = extractHeader(headers, "Range");
        long fileSize = Files.size(filePath);
        long start = 0, endByte = fileSize - 1;
        try {
            String[] parts = rangeHeader.substring(6).trim().split("-", 2);
            if (!parts[0].isEmpty()) start   = Long.parseLong(parts[0].trim());
            if (parts.length > 1 && !parts[1].isEmpty()) endByte = Long.parseLong(parts[1].trim());
        } catch (Exception ignored) {}
        if (start > endByte || start >= fileSize) {
            sendResponse(client, key,
                    ("HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */" + fileSize + "\r\nContent-Length: 0\r\n\r\n").getBytes(), keepAlive);
            return;
        }
        endByte = Math.min(endByte, fileSize - 1);
        long contentLength = endByte - start + 1;
        String header = "HTTP/1.1 206 Partial Content\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Last-Modified: " + HTTP_DATE.format(Files.getLastModifiedTime(filePath).toInstant()) + "\r\n" +
                "Content-Type: " + getContentType(path) + "\r\n" +
                "Content-Length: " + contentLength + "\r\n" +
                "Content-Range: bytes " + start + "-" + endByte + "/" + fileSize + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n";
        try {
            client.write(ByteBuffer.wrap(header.getBytes(StandardCharsets.US_ASCII)));
            try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
                long pos = start, rem = contentLength;
                while (rem > 0) { long s = fc.transferTo(pos, rem, client); if (s <= 0) break; pos += s; rem -= s; }
            }
        } catch (IOException e) { logger.info("Range disconnect: " + e.getMessage()); client.close(); return; }
        logger.info("206: " + path + " " + start + "-" + endByte);
        if (!keepAlive) client.close();
    }

    private void serveFullFile(SocketChannel client, Path filePath,
                               boolean keepAlive, String path) throws IOException {
        long fileSize = Files.size(filePath);
        String header = "HTTP/1.1 200 OK\r\n" +
                "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                "Last-Modified: " + HTTP_DATE.format(Files.getLastModifiedTime(filePath).toInstant()) + "\r\n" +
                "Content-Type: " + getContentType(path) + "\r\n" +
                "Content-Length: " + fileSize + "\r\n" +
                "Accept-Ranges: bytes\r\n" +
                "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n";
        try {
            client.write(ByteBuffer.wrap(header.getBytes(StandardCharsets.US_ASCII)));
            try (FileChannel fc = FileChannel.open(filePath, StandardOpenOption.READ)) {
                long pos = 0, rem = fileSize;
                while (rem > 0) { long s = fc.transferTo(pos, rem, client); if (s <= 0) break; pos += s; rem -= s; }
            }
        } catch (IOException e) { logger.info("Serve disconnect: " + e.getMessage()); client.close(); return; }
        logger.info("200: " + path);
        if (!keepAlive) client.close();
    }

    private Path resolvePath(String path) {
        String folder = "Public", prefix = "";
        if (path.startsWith("/images/")) { folder = "images"; prefix = "/images"; }
        else if (path.startsWith("/files/")) { folder = "documents"; prefix = "/files"; }
        else if (path.startsWith("/video/")) { folder = "video"; prefix = "/video"; }
        else if (path.startsWith("/uploads/")) { folder = "uploads"; prefix = "/uploads"; }
        String rel = prefix.isEmpty() ? path : path.substring(prefix.length());
        if (rel.isEmpty() || rel.equals("/")) return null;
        String fileRel = rel.startsWith("/") ? rel.substring(1) : rel;
        if (fileRel.isEmpty()) return null;
        return Paths.get(BASE, folder, fileRel);
    }

    private boolean isSafePath(Path filePath) {
        return filePath.normalize().toAbsolutePath()
                .startsWith(Paths.get(BASE).toAbsolutePath().normalize());
    }

    private boolean handleCaching(SocketChannel client, SelectionKey key,
                                  String headers, Path filePath, boolean keepAlive) {
        String ims = extractHeader(headers, "If-Modified-Since");
        if (ims == null) return false;
        try {
            ZonedDateTime ct = ZonedDateTime.parse(ims.trim(), HTTP_DATE);
            long lm = Files.getLastModifiedTime(filePath).toMillis() / 1000 * 1000;
            long cm = ct.toInstant().toEpochMilli() / 1000 * 1000;
            if (lm <= cm) {
                String r = "HTTP/1.1 304 Not Modified\r\n" +
                        "Date: " + HTTP_DATE.format(ZonedDateTime.now()) + "\r\n" +
                        "Last-Modified: " + HTTP_DATE.format(Files.getLastModifiedTime(filePath).toInstant()) + "\r\n" +
                        "Connection: " + (keepAlive ? "keep-alive" : "close") + "\r\n\r\n";
                sendResponse(client, key, r.getBytes(StandardCharsets.US_ASCII), keepAlive);
                return true;
            }
        } catch (Exception ignored) {}
        return false;
    }

    //Multipart helpers

    private static String extractBoundary(String contentType) {
        for (String p : contentType.split(";")) {
            p = p.trim();
            if (p.toLowerCase().startsWith("boundary="))
                return p.substring("boundary=".length()).trim();
        }
        return null;
    }

    private static String extractFilename(String disposition) {
        if (disposition == null) return null;
        for (String p : disposition.split(";")) {
            p = p.trim();
            if (p.toLowerCase().startsWith("filename=")) {
                String v = p.substring("filename=".length()).trim();
                if (v.startsWith("\"") && v.endsWith("\"")) v = v.substring(1, v.length()-1);
                return v;
            }
        }
        return null;
    }

    private static int indexOf(byte[] data, byte[] pattern, int from) {
        outer:
        for (int i = from; i <= data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++)
                if (data[i+j] != pattern[j]) continue outer;
            return i;
        }
        return -1;
    }

    private static String extractPartHeader(String partHeaders, String name) {
        for (String line : partHeaders.split("\r\n"))
            if (line.toLowerCase().startsWith(name.toLowerCase() + ":"))
                return line.substring(line.indexOf(':')+1).trim();
        return null;
    }

    private void sendResponse(SocketChannel client, SelectionKey key, byte[] data, boolean keepAlive) {
        try {
            client.write(ByteBuffer.wrap(data));
            if (!keepAlive) cancelAndClose(key, client);
        } catch (IOException e) {
            logger.info("Send error: " + e.getMessage());
            try { cancelAndClose(key, client); } catch (Exception ignored) {}
        }
    }

    private void sendBytes(SocketChannel client, byte[] data) {
        try { client.write(ByteBuffer.wrap(data)); }
        catch (IOException e) { logger.info("Send error: " + e.getMessage()); }
    }

    private void cancelAndClose(SelectionKey key, SocketChannel client) {
        try { key.cancel(); } catch (Exception ignored) {}
        try { client.close(); } catch (Exception ignored) {}
    }

    private static String sendError(int code, String status, String msg) {
        String body = "<h1>" + code + " " + status + "</h1><p>" + msg + "</p>";
        return "HTTP/1.1 " + code + " " + status + "\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.length() + "\r\n\r\n" + body;
    }

    private static String send404() { return sendError(404, "Not Found","Resource not found"); }
    private static String send405() { return sendError(405, "Method Not Allowed","Method not allowed"); }
    private static String send403() { return sendError(403, "Forbidden","Access denied");      }

    private static int findHeaderEnd(byte[] data, int start) {
        for (int i = start; i <= data.length - 4; i++)
            if (data[i]=='\r' && data[i+1]=='\n' && data[i+2]=='\r' && data[i+3]=='\n') return i;
        return -1;
    }

    private static String extractHeader(String headers, String name) {
        for (String line : headers.split("\r\n"))
            if (line.toLowerCase(Locale.ROOT).startsWith(name.toLowerCase(Locale.ROOT) + ":"))
                return line.substring(line.indexOf(':')+1).trim();
        return null;
    }

    private static String getContentType(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html; charset=utf-8";
        if (p.endsWith(".css"))  return "text/css";
        if (p.endsWith(".js"))   return "application/javascript";
        if (p.endsWith(".png"))  return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif"))  return "image/gif";
        if (p.endsWith(".mp4"))  return "video/mp4";
        if (p.endsWith(".webm")) return "video/webm";
        if (p.endsWith(".ogg"))  return "video/ogg";
        if (p.endsWith(".pdf"))  return "application/pdf";
        if (p.endsWith(".json")) return "application/json";
        return "application/octet-stream";
    }

    public static void main(String[] args) {
        SimpleHttpServer server = new SimpleHttpServer();
        server.logger.info("Starting server on port " + PORT);
        new ServerNIO(PORT, server::handleClient).start();
    }
}