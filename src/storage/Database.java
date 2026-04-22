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
        String sql = """
            CREATE TABLE IF NOT EXISTS playlist (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                name    TEXT NOT NULL UNIQUE,
                type    TEXT NOT NULL,
                added_at DATETIME DEFAULT CURRENT_TIMESTAMP
            );
            """;
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
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