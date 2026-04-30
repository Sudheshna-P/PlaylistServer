package service;

import model.MediaFile;
import storage.PlaylistStoreDB;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PlaylistService {

    private final PlaylistStoreDB store;

    public PlaylistService(PlaylistStoreDB store) {
        this.store = store;
    }

    public int create(String name, List<String> files) throws ServiceException {
        if (name == null || name.trim().isEmpty())
            throw new ServiceException(400, "Playlist name cannot be empty");
        if (files == null || files.isEmpty())
            throw new ServiceException(400, "Playlist must have at least one file");
        try {
            if (store.playlistNameExists(name.trim()))
                throw new ServiceException(409, "Playlist name already exists");
            int id = store.createPlaylist(name.trim());
            for (String filename : files) {
                store.addItem(id, filename, detectType(filename));
            }
            return id;
        } catch (ServiceException e) {
            throw e;
        } catch (SQLException e) {
            throw new ServiceException(500, "Database error: " + e.getMessage());
        }
    }

    public List<String[]> getAll() throws ServiceException {
        try {
            List<int[]> rows = store.getAllPlaylists();
            List<String[]> result = new ArrayList<>();
            for (int[] row : rows) {
                int id = row[0];
                String name = store.getPlaylistName(id);
                int count = store.getItemCount(id);
                result.add(new String[]{
                        String.valueOf(id), name, String.valueOf(count)
                });
            }
            return result;
        } catch (SQLException e) {
            throw new ServiceException(500, "Database error: " + e.getMessage());
        }
    }

    public List<MediaFile> getItems(int id) throws ServiceException {
        try {
            if (!store.playlistExists(id))
                throw new ServiceException(404, "Playlist not found");
            List<String[]> rows = store.getItems(id);
            List<MediaFile> result = new ArrayList<>();
            for (String[] row : rows) {
                result.add(new MediaFile(row[0], row[1]));
            }
            return result;
        } catch (ServiceException e) {
            throw e;
        } catch (SQLException e) {
            throw new ServiceException(500, "Database error: " + e.getMessage());
        }
    }

    public void addItem(int playlistId, String filename) throws ServiceException {
        try {
            if (!store.playlistExists(playlistId))
                throw new ServiceException(404, "Playlist not found");
            if (store.itemExists(playlistId, filename))
                throw new ServiceException(409, "File already in playlist");
            store.addItem(playlistId, filename, detectType(filename));
        } catch (ServiceException e) {
            throw e;
        } catch (SQLException e) {
            throw new ServiceException(500, "Database error: " + e.getMessage());
        }
    }

    public void removeItem(int playlistId, String filename) throws ServiceException {
        try {
            if (!store.playlistExists(playlistId))
                throw new ServiceException(404, "Playlist not found");
            store.removeItem(playlistId, filename);
            if (store.getItemCount(playlistId) == 0) {
                store.addItem(playlistId, filename, detectType(filename));
                throw new ServiceException(400, "Cannot remove — playlist would become empty");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (SQLException e) {
            throw new ServiceException(500, "Database error: " + e.getMessage());
        }
    }

    public void delete(int id) throws ServiceException {
        try {
            if (!store.playlistExists(id))
                throw new ServiceException(404, "Playlist not found");
            store.deletePlaylist(id);
        } catch (ServiceException e) {
            throw e;
        } catch (SQLException e) {
            throw new ServiceException(500, "Database error: " + e.getMessage());
        }
    }

    private static String detectType(String filename) {
        return filename.toLowerCase().matches(".*\\.(mp4|webm|ogg|mov|avi)")
                ? "video" : "image";
    }
}