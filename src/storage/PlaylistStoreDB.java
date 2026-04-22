package storage;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PlaylistStoreDB {

    private final Connection connection;

    public PlaylistStoreDB(Database db) {
        this.connection = db.getConnection();
    }

    public List<String> load() throws SQLException {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM playlist ORDER BY added_at ASC";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString("name"));
            }
        }
        return names;
    }

    public void add(String name, String type) throws SQLException {
        String sql = "INSERT OR IGNORE INTO playlist (name, type) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.executeUpdate();
        }
    }

    public void remove(String name) throws SQLException {
        String sql = "DELETE FROM playlist WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
    }

    public boolean exists(String name) throws SQLException {
        String sql = "SELECT 1 FROM playlist WHERE name = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, name);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }
}