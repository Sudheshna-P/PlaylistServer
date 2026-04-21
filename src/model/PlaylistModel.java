package model;

import storage.PlaylistStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PlaylistModel {

    private final PlaylistStore store;
    private final LibraryModel library;

    public PlaylistModel(PlaylistStore store, LibraryModel library) {
        this.store = store;
        this.library = library;
    }

    public List<MediaFile> getAll() throws IOException {
        List<String> names = store.load();
        List<MediaFile> result = new ArrayList<>();
        List<MediaFile> allFiles = library.getAll();

        for (String name : names) {
            // only include if file still exists on disk
            allFiles.stream()
                    .filter(f -> f.getName().equals(name))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }

    public boolean add(String filename) throws IOException {
        List<String> names = store.load();
        if (names.contains(filename)) return false; // already in playlist
        names.add(filename);
        store.save(names);
        return true;
    }

    public boolean remove(String filename) throws IOException {
        List<String> names = store.load();
        boolean removed = names.remove(filename);
        if (removed) store.save(names);
        return removed;
    }
}