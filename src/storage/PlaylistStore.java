package storage;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class PlaylistStore {

    private final Path playlistFile;

    public PlaylistStore(String basePath) {
        this.playlistFile = Paths.get(basePath, "playlist.json");
    }

    public List<String> load() throws IOException {
        if (!Files.exists(playlistFile)) return new ArrayList<>();
        String content = new String(Files.readAllBytes(playlistFile));
        return parseNames(content);
    }

    public void save(List<String> names) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < names.size(); i++) {
            json.append("\"").append(names.get(i)).append("\"");
            if (i < names.size() - 1) json.append(",");
        }
        json.append("]");
        Files.write(playlistFile,
                json.toString().getBytes(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
    }

    private List<String> parseNames(String json) {
        List<String> names = new ArrayList<>();
        json = json.trim();
        if (json.equals("[]") || json.isEmpty()) return names;
        // strip [ and ]
        json = json.substring(1, json.length() - 1);
        for (String part : json.split(",")) {
            String name = part.trim()
                    .replace("\"", "");
            if (!name.isEmpty()) names.add(name);
        }
        return names;
    }
}