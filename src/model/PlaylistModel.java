package model;

import storage.PlaylistStoreDB;

import java.io.IOException;
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

    public List<MediaFile> getAll() throws SQLException, IOException {
        List<String> names = store.load();
        List<MediaFile> result = new ArrayList<>();
        List<MediaFile> allFiles = library.getAll();

        for (String name : names) {
            allFiles.stream()
                    .filter(f -> f.getName().equals(name))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    public boolean add(String filename) throws SQLException, IOException {
        if (store.exists(filename)) return false;
        String type = filename.toLowerCase().matches(".*\\.(mp4|webm|ogg|mov|avi)")
                ? "video" : "image";
        store.add(filename, type);
        return true;
    }

    public boolean remove(String filename) throws SQLException, IOException {
        if (!store.exists(filename)) return false;
        store.remove(filename);
        return true;
    }
}