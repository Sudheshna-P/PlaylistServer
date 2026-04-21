package model;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LibraryModel {

    private final String uploadsPath;

    public LibraryModel(String uploadsPath) {
        this.uploadsPath = uploadsPath;
    }

    public List<MediaFile> getAll() throws IOException {
        List<MediaFile> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(uploadsPath))) {
            for (Path p : stream) {
                if (Files.isDirectory(p)) continue;
                String name = p.getFileName().toString();
                if (name.endsWith(".tmp")) continue;
                files.add(new MediaFile(name, detectType(name)));
            }
        }
        return files;
    }

    private static String detectType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp4") || lower.endsWith(".webm")
                || lower.endsWith(".ogg") || lower.endsWith(".mov")
                || lower.endsWith(".avi")) {
            return "video";
        }
        return "image";
    }
}