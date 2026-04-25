package storage;

import java.sql.*;
import java.nio.file.*;

public class Database {

    private static final String DB_FILE = "playlist.db";
    private final Connection connection;

    public Database(String basePath) throws SQLException {
        String url = "jdbc:sqlite:" + Paths.get(basePath, DB_FILE);
        this.connection = DriverManager.getConnection(url);
        createTables();
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS playlists (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                name       TEXT NOT NULL UNIQUE,
                created_at DATETIME DEFAULT CURRENT_TIMESTAMP
            )
            """);
            stmt.execute("""
            CREATE TABLE IF NOT EXISTS playlist_items (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                playlist_id INTEGER NOT NULL,
                media_name  TEXT NOT NULL,
                media_type  TEXT NOT NULL,
                position    INTEGER NOT NULL DEFAULT 0,
                FOREIGN KEY (playlist_id) REFERENCES playlists(id)
            )
            """);
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public void close() {
        try { connection.close(); }
        catch (SQLException ignored) {}
    }
}