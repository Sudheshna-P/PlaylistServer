package model;

import http.HttpParser;

import java.io.IOException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;

public class UploadModel {

    private final String uploadsPath;

    public UploadModel(String uploadsPath) {
        this.uploadsPath = uploadsPath;
    }

    public void parse(byte[] body, String boundary,
                      List<String> saved, List<String> errors) {

        byte[] delimiter = ("\r\n--" + boundary).getBytes();
        byte[] firstBoundary = ("--" + boundary).getBytes();

        int pos = HttpParser.indexOf(body, firstBoundary, 0);
        if (pos == -1) { errors.add("Malformed multipart body"); return; }
        pos += firstBoundary.length;

        while (pos < body.length) {
            if (pos + 1 < body.length && body[pos] == '-' && body[pos+1] == '-') break;
            if (pos + 1 < body.length && body[pos] == '\r' && body[pos+1] == '\n') pos += 2;
            else break;

            int partHeaderEnd = HttpParser.indexOf(body,
                    new byte[]{'\r','\n','\r','\n'}, pos);
            if (partHeaderEnd == -1) break;

            String partHeaders = new String(body, pos,
                    partHeaderEnd - pos);
            int dataStart = partHeaderEnd + 4;

            int dataEnd = HttpParser.indexOf(body, delimiter, dataStart);
            if (dataEnd == -1) dataEnd = body.length;

            String disposition = HttpParser.extractPartHeader(partHeaders, "Content-Disposition");
            String filename = HttpParser.extractFilename(disposition);

            if (filename == null || filename.isEmpty()) {
                pos = dataEnd + delimiter.length;
                continue;
            }

            filename = Paths.get(filename).getFileName().toString()
                    .replaceAll("[^a-zA-Z0-9._\\-]", "_");

            Path savePath = Paths.get(uploadsPath, filename);
            if (Files.exists(savePath)) {
                String ts = String.valueOf(System.currentTimeMillis());
                String ext = filename.contains(".")
                        ? filename.substring(filename.lastIndexOf('.')) : "";
                String base = filename.contains(".")
                        ? filename.substring(0, filename.lastIndexOf('.')) : filename;
                filename = base + "_" + ts + ext;
                savePath = Paths.get(uploadsPath, filename);
            }

            try {
                Files.write(savePath,
                        Arrays.copyOfRange(body, dataStart, dataEnd),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
                saved.add(filename);
            } catch (IOException e) {
                errors.add(filename + " (" + e.getMessage() + ")");
            }

            pos = dataEnd + delimiter.length;
        }
    }
}