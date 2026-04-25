package model;

import storage.PlaylistStoreDB;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PlaylistModel {

    private final PlaylistStoreDB store;
    private final LibraryModel library;

    public PlaylistModel(PlaylistStoreDB store, LibraryModel library) {
        this.store = store;
        this.library = library;
    }

    // ── create ────────────────────────────────────────────────────────────

    public int create(String name, List<String> filenames)
            throws SQLException, IllegalArgumentException {

        if (name == null || name.trim().isEmpty())
            throw new IllegalArgumentException("Playlist name cannot be empty");

        if (filenames == null || filenames.isEmpty())
            throw new IllegalArgumentException("Playlist must have at least one file");

        if (store.playlistNameExists(name.trim()))
            throw new IllegalArgumentException("Playlist name already exists");

        int id = store.createPlaylist(name.trim());

        for (String filename : filenames) {
            String type = detectType(filename);
            store.addItem(id, filename, type);
        }
        return id;
    }

    // ── read ──────────────────────────────────────────────────────────────

    public List<String[]> getAllPlaylists() throws SQLException {
        List<int[]> rows = store.getAllPlaylists();
        List<String[]> result = new ArrayList<>();
        for (int[] row : rows) {
            int id = row[0];
            String name = store.getPlaylistName(id);
            int count = store.getItemCount(id);
            result.add(new String[]{
                    String.valueOf(id),
                    name,
                    String.valueOf(count)
            });
        }
        return result;
    }

    public List<MediaFile> getPlaylistItems(int id)
            throws SQLException, IllegalArgumentException {
        if (!store.playlistExists(id))
            throw new IllegalArgumentException("Playlist not found");

        List<String[]> items = store.getItems(id);
        List<MediaFile> result = new ArrayList<>();
        for (String[] item : items) {
            result.add(new MediaFile(item[0], item[1]));
        }
        return result;
    }

    // ── edit ──────────────────────────────────────────────────────────────

    public void addItem(int playlistId, String filename)
            throws SQLException, IllegalArgumentException {
        if (!store.playlistExists(playlistId))
            throw new IllegalArgumentException("Playlist not found");
        if (store.itemExists(playlistId, filename))
            throw new IllegalArgumentException("File already in playlist");
        store.addItem(playlistId, filename, detectType(filename));
    }

    public void removeItem(int playlistId, String filename)
            throws SQLException, IllegalArgumentException {
        if (!store.playlistExists(playlistId))
            throw new IllegalArgumentException("Playlist not found");

        store.removeItem(playlistId, filename);

        // enforce non-empty rule
        if (store.getItemCount(playlistId) == 0)
            throw new IllegalArgumentException(
                    "Cannot remove — playlist would become empty");
    }

    // ── delete ────────────────────────────────────────────────────────────

    public void deletePlaylist(int id)
            throws SQLException, IllegalArgumentException {
        if (!store.playlistExists(id))
            throw new IllegalArgumentException("Playlist not found");
        store.deletePlaylist(id);
    }

    // ── util ──────────────────────────────────────────────────────────────

    private static String detectType(String filename) {
        String lower = filename.toLowerCase();
        return lower.matches(".*\\.(mp4|webm|ogg|mov|avi)") ? "video" : "image";
    }
}