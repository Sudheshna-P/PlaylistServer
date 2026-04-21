package util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class PathResolver {

    private static final String BASE = "/home/sudheshna/IdeaProjects/PlaylistServer/src";

    private PathResolver() {}

    public static Path resolve(String path) {
        String folder = "Public", prefix = "";
        if (path.startsWith("/images/"))       { folder = "images";    prefix = "/images"; }
        else if (path.startsWith("/files/"))   { folder = "documents"; prefix = "/files"; }
        else if (path.startsWith("/video/"))   { folder = "video";     prefix = "/video"; }
        else if (path.startsWith("/uploads/")) { folder = "uploads";   prefix = "/uploads"; }

        String rel = prefix.isEmpty() ? path : path.substring(prefix.length());
        if (rel.isEmpty() || rel.equals("/")) return null;
        String fileRel = rel.startsWith("/") ? rel.substring(1) : rel;
        if (fileRel.isEmpty()) return null;
        return Paths.get(BASE, folder, fileRel);
    }

    public static boolean isSafe(Path filePath) {
        return filePath.normalize().toAbsolutePath()
                .startsWith(Paths.get(BASE).toAbsolutePath().normalize());
    }
}