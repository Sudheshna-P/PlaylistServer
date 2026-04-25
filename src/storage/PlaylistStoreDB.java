package storage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PlaylistStoreDB {

    private final Connection connection;

    public PlaylistStoreDB(Database db) {
        this.connection = db.getConnection();
    }

    // ── playlists table ───────────────────────────────────────────────────

    public int createPlaylist(String name) throws SQLException {
        String sql = "INSERT INTO playlists (name) VALUES (?)";
        try (PreparedStatement ps = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
            throw new SQLException("Failed to get playlist id");
        }
    }

    public List<int[]> getAllPlaylists() throws SQLException {
        List<int[]> result = new ArrayList<>();
        String sql = "SELECT id FROM playlists ORDER BY created_at ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(new int[]{rs.getInt("id")});
            }
        }
        return result;
    }

    public String getPlaylistName(int id) throws SQLException {
        String sql = "SELECT name FROM playlists WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("name");
            return null;
        }
    }

    public boolean playlistExists(int id) throws SQLException {
        String sql = "SELECT 1 FROM playlists WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            return ps.executeQuery().next();
        }
    }

    public boolean playlistNameExists(String name) throws SQLException {
        String sql = "SELECT 1 FROM playlists WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            return ps.executeQuery().next();
        }
    }

    public void deletePlaylist(int id) throws SQLException {
        // delete items first
        String deleteItems = "DELETE FROM playlist_items WHERE playlist_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(deleteItems)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
        String deletePlaylist = "DELETE FROM playlists WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(deletePlaylist)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ── playlist_items table ──────────────────────────────────────────────

    public void addItem(int playlistId, String name, String type) throws SQLException {
        // get current max position
        String maxPos = "SELECT COALESCE(MAX(position), 0) FROM playlist_items WHERE playlist_id = ?";
        int position = 0;
        try (PreparedStatement ps = connection.prepareStatement(maxPos)) {
            ps.setInt(1, playlistId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) position = rs.getInt(1) + 1;
        }
        String sql = "INSERT INTO playlist_items (playlist_id, media_name, media_type, position) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, playlistId);
            ps.setString(2, name);
            ps.setString(3, type);
            ps.setInt(4, position);
            ps.executeUpdate();
        }
    }

    public void removeItem(int playlistId, String name) throws SQLException {
        String sql = "DELETE FROM playlist_items WHERE playlist_id = ? AND media_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, playlistId);
            ps.setString(2, name);
            ps.executeUpdate();
        }
    }

    public boolean itemExists(int playlistId, String name) throws SQLException {
        String sql = "SELECT 1 FROM playlist_items WHERE playlist_id = ? AND media_name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, playlistId);
            ps.setString(2, name);
            return ps.executeQuery().next();
        }
    }

    public int getItemCount(int playlistId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM playlist_items WHERE playlist_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, playlistId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
            return 0;
        }
    }

    public List<String[]> getItems(int playlistId) throws SQLException {
        List<String[]> items = new ArrayList<>();
        String sql = "SELECT media_name, media_type FROM playlist_items " +
                "WHERE playlist_id = ? ORDER BY position ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, playlistId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                items.add(new String[]{
                        rs.getString("media_name"),
                        rs.getString("media_type")
                });
            }
        }
        return items;
    }

    // kept for backward compat — old single playlist methods
    public List<String> load() throws SQLException {
        return new ArrayList<>();
    }

    public void add(String name, String type) throws SQLException {}
    public void remove(String name) throws SQLException {}
    public boolean exists(String name) throws SQLException { return false; }
}